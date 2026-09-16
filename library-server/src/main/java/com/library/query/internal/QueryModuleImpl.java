package com.library.query.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.BusinessDate;
import com.library.query.contract.ArrivalQuery;
import com.library.query.contract.MyBusinessQuery;
import com.library.query.internal.adapter.persistence.QueryStore;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * QUERY 模块实现：只读组合可见快照，不办理状态变更（MLS-DOC-MD-0002 §3）。
 */
@Component
public class QueryModuleImpl implements ArrivalQuery, MyBusinessQuery {

    private static final Set<String> KINDS = Set.of("RESERVATION", "LOAN", "FINE", "ORDER");

    private final QueryStore store;

    public QueryModuleImpl(QueryStore store) {
        this.store = store;
    }

    @Override
    public ModuleResult<ArrivalDashboard> arrivals(BusinessDate date, ActorContext actor) {
        var counts = store.arrivals(date.value());
        return ModuleResult.Accepted.of(new ArrivalDashboard(date, counts.expected(),
                counts.arrived(), counts.pending(), counts.exceptions()));
    }

    @Override
    public ModuleResult<MyBusinessPage> myBusiness(MyBusinessCriteria criteria, ActorContext actor) {
        var kind = criteria.kind() == null ? null : criteria.kind().toUpperCase(Locale.ROOT);
        if (kind != null && !KINDS.contains(kind)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_QUERY_001.code(), "未知业务类别");
        }
        var cursor = criteria.page() == null ? PageCursor.first() : criteria.page();
        var beforeId = cursor.cursor() == null ? Long.MAX_VALUE : Long.parseLong(cursor.cursor());
        var wanted = kind == null ? List.of("RESERVATION", "LOAN", "FINE") : List.of(kind);
        var rows = store.myBusiness(actor.accountId().value(), wanted, criteria.status(),
                beforeId, cursor.limit() + 1);
        var hasNext = rows.size() > cursor.limit();
        var pageRows = rows.stream().limit(cursor.limit()).toList();
        var items = pageRows.stream().map(row -> new MyBusinessQuery.BusinessItem(
                row.kind(), row.id(), row.status(), row.title(),
                row.businessDate() == null ? null : row.businessDate().toString(),
                row.amountCents())).toList();
        var nextCursor = hasNext && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).id()) : null;
        return ModuleResult.Accepted.of(new MyBusinessQuery.MyBusinessPage(items, nextCursor));
    }
}
