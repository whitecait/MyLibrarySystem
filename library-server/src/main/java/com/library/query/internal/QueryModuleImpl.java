package com.library.query.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.GuaranteeType;
import com.library.circulation.contract.model.TxId;
import com.library.identity.contract.model.ReaderId;
import com.library.settlement.contract.model.FineId;
import com.library.query.contract.ArrivalQuery;
import com.library.query.contract.MyBusinessQuery;
import com.library.circulation.contract.CirculationReadSnapshots;
import com.library.settlement.contract.SettlementReadSnapshots;
import com.library.catalog.contract.CatalogReadSnapshots;
import com.library.catalog.contract.model.TitleId;
import com.library.delivery.contract.NotificationQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * QUERY 模块实现：只读组合可见快照，不办理状态变更（MLS-DOC-MD-0002 §3）。
 */
@Component
public class QueryModuleImpl implements ArrivalQuery, MyBusinessQuery {

    private static final Set<String> KINDS = Set.of("RESERVATION", "LOAN", "FINE", "ORDER");
    private static final Set<String> GROUPS = Set.of("CURRENT_LOANS", "RESERVATIONS", "HISTORY");

    private final CirculationReadSnapshots circulation;
    private final SettlementReadSnapshots settlement;
    private final CatalogReadSnapshots catalog;
    private final NotificationQuery notifications;

    public QueryModuleImpl(CirculationReadSnapshots circulation, SettlementReadSnapshots settlement,
                           CatalogReadSnapshots catalog, NotificationQuery notifications) {
        this.circulation = circulation;
        this.settlement = settlement;
        this.catalog = catalog;
        this.notifications = notifications;
    }

    private record BusinessRow(String kind, long id, String status, String title,
                               LocalDate businessDate, Long amountCents, String pickupSlot,
                               Long copyId, GuaranteeType guaranteeType) {}

    @Override
    public ModuleResult<ArrivalDashboard> arrivals(BusinessDate date, ActorContext actor) {
        var result = circulation.arrivals(date);
        if (result instanceof ModuleResult.Rejected<CirculationReadSnapshots.ArrivalCounts> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var counts = ((ModuleResult.Accepted<CirculationReadSnapshots.ArrivalCounts>) result).value();
        return ModuleResult.Accepted.of(new ArrivalDashboard(date, counts.expected(),
                counts.arrived(), counts.pending(), counts.exceptions()));
    }

    @Override
    public ModuleResult<MyBusinessPage> myBusiness(MyBusinessCriteria criteria, ActorContext actor) {
        var kind = criteria.kind() == null ? null : criteria.kind().toUpperCase(Locale.ROOT);
        if (kind != null && !KINDS.contains(kind)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_QUERY_001.code(), "未知业务类别");
        }
        var group = criteria.group() == null ? null : criteria.group().toUpperCase(Locale.ROOT);
        if (group != null && !GROUPS.contains(group)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_QUERY_001.code(), "未知业务分组");
        }
        if (group != null && kind != null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_QUERY_001.code(), "业务分组不能与业务类别同时使用");
        }
        var cursor = criteria.page() == null ? PageCursor.first() : criteria.page();
        long beforeId = Long.MAX_VALUE;
        String afterKind = "";
        if (cursor.cursor() != null) {
            var parts = cursor.cursor().split(":", -1);
            try {
                beforeId = Long.parseLong(parts[0]);
                if (beforeId <= 0 || parts.length > 2 || (parts.length == 2 && !KINDS.contains(parts[1]))) {
                    throw new IllegalArgumentException("invalid cursor");
                }
                // Legacy numeric cursors retain their exclusive-ID meaning.
                afterKind = parts.length == 2 ? parts[1] : null;
            } catch (IllegalArgumentException invalid) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_QUERY_001.code(), "无效业务分页游标");
            }
        }
        var wanted = group == null
                ? (kind == null ? List.of("RESERVATION", "LOAN", "FINE") : List.of(kind))
                : kindsForGroup(group);
        var status = criteria.status() == null || criteria.status().isBlank() ? null : criteria.status();
        var transactionStatuses = group == null
                ? (status == null ? null : List.of(status))
                : statusesForGroup(group, status);
        var snapshots = new ArrayList<BusinessRow>();
        // Fetch one extra row beyond lookahead because each owner may return the inclusive anchor.
        var transactionResult = circulation.readerTransactions(ReaderId.of(actor.accountId().value()), wanted,
                transactionStatuses, TxId.of(beforeId), cursor.limit() + 2);
        if (transactionResult instanceof ModuleResult.Rejected<List<CirculationReadSnapshots.TransactionSnapshot>> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var transactions = ((ModuleResult.Accepted<List<CirculationReadSnapshots.TransactionSnapshot>>) transactionResult).value();
        var nameResult = catalog.titleNames(transactions.stream().map(
                CirculationReadSnapshots.TransactionSnapshot::titleId).distinct().toList());
        if (nameResult instanceof ModuleResult.Rejected<Map<TitleId, String>> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var names = ((ModuleResult.Accepted<Map<TitleId, String>>) nameResult).value();
        for (var fact : transactions) {
            snapshots.add(new BusinessRow(fact.kind(), fact.id().value(), fact.status(), names.get(fact.titleId()),
                    fact.businessDate() == null ? null : fact.businessDate().value(), null,
                    fact.pickupSlot(), fact.copyId(), fact.guaranteeType()));
        }
        if (wanted.contains("FINE")) {
            var fineResult = settlement.readerFines(ReaderId.of(actor.accountId().value()), status,
                    FineId.of(beforeId), cursor.limit() + 2);
            if (fineResult instanceof ModuleResult.Rejected<List<SettlementReadSnapshots.FineSnapshot>> rejected) {
                return ModuleResult.Rejected.propagate(rejected);
            }
            for (var fact : ((ModuleResult.Accepted<List<SettlementReadSnapshots.FineSnapshot>>) fineResult).value()) {
                snapshots.add(new BusinessRow("FINE", fact.id().value(), fact.status(), null, null,
                        fact.amountCents().value(), null, null, null));
            }
        }
        final long anchorId = beforeId;
        final String anchorKind = afterKind;
        var rows = snapshots.stream()
                .filter(row -> row.id() < anchorId || (row.id() == anchorId && anchorKind != null
                        && row.kind().compareTo(anchorKind) > 0))
                .sorted(java.util.Comparator.comparingLong(BusinessRow::id).reversed()
                        .thenComparing(BusinessRow::kind))
                .limit(cursor.limit() + 1).toList();
        var hasNext = rows.size() > cursor.limit();
        var pageRows = rows.stream().limit(cursor.limit()).toList();
        var transactionIds = pageRows.stream()
                .filter(row -> row.kind().equals("RESERVATION") || row.kind().equals("LOAN"))
                .map(BusinessRow::id).distinct().toList();
        var notificationsByTransaction = new java.util.HashMap<Long, List<MyBusinessQuery.NotificationFact>>();
        if (!transactionIds.isEmpty()) {
            var notificationResult = notifications.notificationsForTransactions(transactionIds, actor);
            if (notificationResult instanceof ModuleResult.Rejected<List<NotificationQuery.Notification>> rejected) {
                return ModuleResult.Rejected.propagate(rejected);
            }
            for (var notification : ((ModuleResult.Accepted<List<NotificationQuery.Notification>>) notificationResult).value()) {
                if (notification.relatedTxId() == null) continue;
                notificationsByTransaction.computeIfAbsent(notification.relatedTxId(), ignored -> new ArrayList<>())
                        .add(new MyBusinessQuery.NotificationFact(notification.notificationId(),
                                notification.type(), notification.read(), notification.reservationDate(),
                                notification.createdAt()));
            }
        }
        var items = pageRows.stream().map(row -> new MyBusinessQuery.BusinessItem(
                row.kind(), row.id(), row.status(), row.title(),
                row.businessDate() == null ? null : row.businessDate().toString(),
                row.amountCents(), row.businessDate() == null ? null : row.businessDate().toString(),
                row.pickupSlot(), row.copyId(), row.guaranteeType(),
                row.kind().equals("RESERVATION") || row.kind().equals("LOAN")
                        ? notificationsByTransaction.getOrDefault(row.id(), List.of()) : List.of())).toList();
        var nextCursor = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).id() + ":" + pageRows.get(pageRows.size() - 1).kind() : null;
        return ModuleResult.Accepted.of(new MyBusinessQuery.MyBusinessPage(items, nextCursor));
    }

    private List<String> kindsForGroup(String group) {
        return switch (group) {
            case "CURRENT_LOANS" -> List.of("LOAN");
            case "RESERVATIONS" -> List.of("RESERVATION");
            case "HISTORY" -> List.of("RESERVATION", "LOAN");
            default -> List.of();
        };
    }

    private List<String> statusesForGroup(String group, String status) {
        List<String> allowed = switch (group) {
            case "CURRENT_LOANS" -> List.of("LENT");
            case "RESERVATIONS" -> List.of("AWAIT", "CONFIRMED", "MANUAL_HANDLING", "WAITLIST");
            case "HISTORY" -> List.of("NOT_BORROWED", "RETURNED", "RETURN_ABNORMAL");
            default -> List.of();
        };
        return status == null ? allowed : allowed.contains(status) ? List.of(status) : List.of();
    }
}
