package com.library.circulation.internal;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.circulation.contract.AvailabilityQuery;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.ReaderCirculationQuery;
import com.library.circulation.contract.Transaction;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.circulation.contract.model.GuaranteeType;
import com.library.circulation.contract.model.PickupSlot;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;
import com.library.circulation.internal.adapter.persistence.CirculationStore;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.OptionalLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CIRCULATION 模块实现：副本六态、预约/候补、借出、人工处置、续借、归还。
 * 规则与持久化细节隐藏在模块内；DB 副本态 SHELF_READY/LENT 在契约边界映射
 * API 态 AVAILABLE/BORROWED。借期与额度遵循 RA-0011。
 * 维护完成刷新结算（DEF-M6-012）：SHELVE 使副本回到可借后按队头先到先得分配候补并
 * 登记 WAITLIST_ASSIGNED 事件；刷新结算仅分配、不清退（清退在闭馆结算）。
 */
@Component
public class CirculationModuleImpl implements AvailabilityQuery, ReservationCommands,
        LoanCommands, ReturnCommands, CopyCommands, ReaderCirculationQuery {

    /** RA-0011: initial term and each renewal are at most seven days. */
    public static final int LOAN_DAYS = 7;
    public static final int RENEWAL_DAYS = 7;
    public static final int MAX_TOTAL_LOAN_DAYS = 30;

    private static final Map<String, String> API_TO_DB_STATUS = Map.of(
            "PENDING_SHELVING", "PENDING_SHELVING",
            "AVAILABLE", "SHELF_READY",
            "RESERVED", "RESERVED",
            "BORROWED", "LENT",
            "MAINTENANCE", "MAINTENANCE",
            "REMOVED", "REMOVED");
    private static final Map<String, String> DB_TO_API_STATUS = Map.of(
            "PENDING_SHELVING", "PENDING_SHELVING",
            "SHELF_READY", "AVAILABLE",
            "RESERVED", "RESERVED",
            "LENT", "BORROWED",
            "MAINTENANCE", "MAINTENANCE",
            "REMOVED", "REMOVED");

    private final CirculationStore store;
    private final OutboxAppender outbox;
    private final AuditAppender audit;
    private final BusinessClock clock;

    public CirculationModuleImpl(CirculationStore store, OutboxAppender outbox, AuditAppender audit,
                                 BusinessClock clock) {
        this.store = store;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void lockReader(long readerId) {
        store.lockReaderActivity(readerId);
    }

    @Override
    public void lockTitleReservations(long titleId) {
        store.lockTitleReservations(titleId);
    }

    @Override
    public ReaderActivity activity(long readerId, LocalDate onDate) {
        return new ReaderActivity(store.countActiveLoansForReader(readerId),
                store.countActiveReservationsForReader(readerId),
                store.hasOverdueLentForReader(readerId, onDate));
    }

    @Override
    public OptionalLong readerForTransaction(long txId) {
        return store.txById(txId).map(tx -> OptionalLong.of(tx.readerId())).orElseGet(OptionalLong::empty);
    }

    @Override
    public OptionalLong titleForTransaction(long txId) {
        return store.txById(txId).map(tx -> OptionalLong.of(tx.titleId())).orElseGet(OptionalLong::empty);
    }

    @Override
    public OptionalLong readerForOpenCopy(long copyId) {
        return store.openLentByCopy(copyId).map(tx -> OptionalLong.of(tx.readerId())).orElseGet(OptionalLong::empty);
    }

    // ---- 可用性 -------------------------------------------------------------

    @Override
    public ModuleResult<AvailabilitySnapshot> availability(TitleId titleId, BusinessDate date) {
        var copies = store.countCopies(titleId.value());
        var available = store.countAvailableCopies(titleId.value());
        var state = copies == 0 ? "UNKNOWN" : (available > 0 ? "AVAILABLE" : "UNAVAILABLE");
        return ModuleResult.Accepted.of(new AvailabilitySnapshot(titleId, state, available));
    }

    @Override
    public ModuleResult<ReservationCalendar> calendar(TitleId titleId, BusinessDate from, BusinessDate to) {
        if (to.value().isBefore(from.value())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "日期区间无效（to 早于 from）");
        }
        if (from.value().isAfter(clock.today().plusMonths(2))) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "日期区间过远（超过两个月）");
        }
        var available = store.countAvailableCopies(titleId.value());
        var total = store.countCopies(titleId.value());
        var days = new ArrayList<CalendarDay>();
        for (var day = from.value(); !day.isAfter(to.value()); day = day.plusDays(1)) {
            String state;
            if (total == 0) {
                state = "UNAVAILABLE";
            } else {
                var confirmed = store.countConfirmedForDate(titleId.value(), day);
                state = confirmed < available ? "GUARANTEED" : "NOT_GUARANTEED";
            }
            days.add(new CalendarDay(BusinessDate.of(day), state));
        }
        return ModuleResult.Accepted.of(new ReservationCalendar(days));
    }

    // ---- 预约 -----------------------------------------------------------------

    @Override
    public ModuleResult<Reservation> reserve(ReservationCommand command) {
        if (!PickupSlot.isValid(command.pickupSlot())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "预约时段无效");
        }
        var today = clock.today();
        var channel = command.channel() == null ? "" : command.channel().toUpperCase(Locale.ROOT);
        if (!List.of("ONLINE", "DESK").contains(channel)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "预约渠道无效");
        }
        if (command.reservationDate() == null || command.reservationDate().value().isBefore(today)
                || ("ONLINE".equals(channel) && !command.reservationDate().value().isAfter(today))) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(),
                    "线上预约最早从明日开始；到台预约日期不得早于今日");
        }
        if (store.countActiveReservationsForReader(command.readerId())
                >= ReservationCommands.MAX_ACTIVE_RESERVATIONS) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "读者已达到五项活动预约上限");
        }
        if (store.hasOpenTransactionForReaderTitle(command.readerId(), command.titleId().value())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_003.code(), "同一书目已有未终结的借阅事务");
        }
        if (store.countBookableCopies(command.titleId().value()) == 0) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "书目当前没有可预约馆藏副本");
        }
        var bound = bindFirstAvailable(command.titleId().value());
        if (bound != null) {
            var txId = store.insertTransaction(new CirculationStore.NewTransaction(
                    command.readerId(), command.titleId().value(), bound, "CONFIRMED",
                    command.reservationDate().value(), command.pickupSlot(), null,
                    GuaranteeType.DIRECT_CONFIRMATION));
            return ModuleResult.Accepted.of(new Reservation(TxId.of(txId), "CONFIRMED", List.of()));
        }
        if ("DESK".equals(channel) && command.reservationDate().value().equals(today)) {
            var txId = store.insertTransaction(new CirculationStore.NewTransaction(
                    command.readerId(), command.titleId().value(), null, "WAITLIST",
                    command.reservationDate().value(), command.pickupSlot(), null, GuaranteeType.WAITLIST));
            return ModuleResult.Accepted.of(new Reservation(TxId.of(txId), "WAITLIST_ENQUEUED", List.of()));
        }
        if (!command.confirmWaitlist()) {
            // 待确认：不创建业务事实，确认请求复送完整字段+新幂等键重新判定
            return ModuleResult.Accepted.of(new Reservation(null, "WAITLIST_PENDING_CONFIRMATION", List.of()));
        }
        var txId = store.insertTransaction(new CirculationStore.NewTransaction(
                command.readerId(), command.titleId().value(), null, "WAITLIST",
                command.reservationDate().value(), command.pickupSlot(), null, GuaranteeType.WAITLIST));
        return ModuleResult.Accepted.of(new Reservation(TxId.of(txId), "WAITLIST_ENQUEUED", List.of()));
    }

    @Override
    public ModuleResult<Transaction> cancel(CancelReservation command) {
        var tx = store.txById(command.txId().value()).orElse(null);
        if (tx == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_003.code(), "借阅事务不存在");
        }
        var isOwner = tx.readerId() == command.actor().accountId().value();
        if (!isOwner && command.actor().role() != com.library.contract.model.Role.LIBRARIAN) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "仅本人或馆员可取消预约");
        }
        var cancellable = List.of("AWAIT", "CONFIRMED", "WAITLIST", "MANUAL_HANDLING");
        if (!cancellable.contains(tx.status())) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), tx.version(),
                    "事务已处于终态或借出中，不能取消");
        }
        var updated = store.transitionTransaction(command.txId().value(), tx.status(), "NOT_BORROWED",
                tx.version());
        if (updated == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), tx.version(),
                    "事务状态已变化，请重新确认");
        }
        releaseBoundCopy(tx);
        return ModuleResult.Accepted.of(new Transaction(command.txId(), "CANCELLED", "NOT_BORROWED"));
    }

    // ---- 借出 -----------------------------------------------------------------

    @Override
    public ModuleResult<LoanBatch> lend(WalkInLoanCommand command) {
        if (command.actor() == null
                || command.actor().role() != com.library.contract.model.Role.LIBRARIAN) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "仅馆员可办理现场借出");
        }
        if (!"DESK".equalsIgnoreCase(command.channel())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "现场借出仅支持馆员到台办理");
        }
        var items = new ArrayList<LoanBatchItem>();
        int ref = 0;
        for (var item : command.items()) {
            ref++;
            var itemRef = item.copyId() != null ? item.copyId().asString() : item.titleId().asString();
            items.add(lendSingle(command.readerId(), item, itemRef));
        }
        return ModuleResult.Accepted.of(new LoanBatch(items));
    }

    private LoanBatchItem lendSingle(long readerId, WalkInItem item, String itemRef) {
        if (store.countActiveLoansForReader(readerId) >= LoanCommands.MAX_ACTIVE_LOANS) {
            return new LoanBatchItem(itemRef, null, "REJECTED", List.of("ACTIVE_LOAN_LIMIT"));
        }
        if (store.hasOpenTransactionForReaderTitle(readerId, item.titleId().value())) {
            return new LoanBatchItem(itemRef, null, "REJECTED", List.of("ALREADY_BORROWED"));
        }
        var candidates = new ArrayList<CirculationStore.CopyRow>();
        if (item.copyId() != null) {
            store.copyById(item.copyId().value())
                    .filter(copy -> copy.titleId() == item.titleId().value()
                            && "SHELF_READY".equals(copy.status()))
                    .ifPresent(candidates::add);
        } else {
            candidates.addAll(store.loanableCopies(item.titleId().value(), clock.today(), 8));
        }
        for (var copy : candidates) {
            var newVersion = store.transitionCopyToLent(
                    copy.id(), "SHELF_READY", copy.version(), clock.today());
            if (newVersion == null) {
                continue; // 同副本竞争（X1）失败，尝试下一候选
            }
            var loanStarted = clock.today();
            var dueDate = loanStarted.plusDays(LOAN_DAYS);
            var txId = store.insertTransaction(new CirculationStore.NewTransaction(
                    readerId, item.titleId().value(), copy.id(), "LENT",
                    loanStarted, null, dueDate));
            store.setLoanStartedDate(txId, loanStarted);
            return new LoanBatchItem(itemRef, TxId.of(txId), "LENT", List.of());
        }
        return new LoanBatchItem(itemRef, null, "REJECTED", List.of("COPY_NOT_AVAILABLE"));
    }

    @Override
    public ModuleResult<Transaction> claim(ClaimPickup command) {
        var tx = store.txById(command.txId().value()).orElse(null);
        if (tx == null || !"CONFIRMED".equals(tx.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "无效取书对象（非已确认预约）");
        }
        if (tx.copyId() == null || tx.copyId() != command.copyId().value()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "副本与预约绑定不一致");
        }
        var copy = store.copyById(command.copyId().value()).orElse(null);
        if (copy == null || !"RESERVED".equals(copy.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "副本不在预约保留状态");
        }
        if (!tx.reservationDate().equals(clock.today())) {
            var manualVersion = store.transitionTransaction(
                    tx.id(), "CONFIRMED", "MANUAL_HANDLING", tx.version());
            if (manualVersion == null) {
                return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), tx.version(),
                        "事务状态已变化，请重新确认");
            }
            return ModuleResult.Accepted.of(new Transaction(
                    command.txId(), "MANUAL_HANDLING", "MANUAL_HANDLING"));
        }
        // 展示与提交之间状态可能变化：提交时重验（X4）
        var copyVersion = store.transitionCopyToLent(
                copy.id(), "RESERVED", copy.version(), clock.today());
        if (copyVersion == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), copy.version(),
                    "副本状态已变化或尚未到可借业务日，请重新确认");
        }
        var txVersion = store.transitionTransaction(tx.id(), "CONFIRMED", "LENT", tx.version());
        if (txVersion == null) {
            store.transitionCopy(copy.id(), "LENT", "RESERVED", copyVersion);
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), tx.version(),
                    "事务状态已变化，请重新确认");
        }
        var loanStarted = clock.today();
        store.setLoanStartedDate(tx.id(), loanStarted);
        store.setDueDate(tx.id(), loanStarted.plusDays(LOAN_DAYS));
        return ModuleResult.Accepted.of(new Transaction(command.txId(), "CLAIMED", "LENT"));
    }

    @Override
    public ModuleResult<Transaction> applyManualAction(ManualAction command) {
        var tx = store.txById(command.txId().value()).orElse(null);
        if (tx == null || !"MANUAL_HANDLING".equals(tx.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "事务不在人工处置中");
        }
        var action = command.action() == null ? "" : command.action().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "RELEASE" -> releaseFromManual(tx);
            case "CONFIRM_ENTRY" -> confirmEntryFromManual(tx);
            case "REJECT" -> rejectFromManual(tx);
            default -> ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "未知人工处置动作");
        };
    }

    private ModuleResult<Transaction> releaseFromManual(CirculationStore.TxRow tx) {
        var alreadyBound = tx.copyId() != null;
        var bound = alreadyBound ? tx.copyId() : bindFirstLoanable(tx.titleId());
        if (bound == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "无可用副本，保持人工处置中");
        }
        var copy = store.copyById(bound).orElse(null);
        if (copy == null || copy.titleId() != tx.titleId() || !"RESERVED".equals(copy.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "绑定副本不在已预定状态，保持人工处置中");
        }
        var copyVersion = store.transitionCopyToLent(bound, "RESERVED", copy.version(), clock.today());
        if (copyVersion == null) {
            if (!alreadyBound) {
                // 日期守卫拒绝借出时，撤销本次临时绑定，避免副本被卡在 RESERVED。
                store.transitionCopy(bound, "RESERVED", "SHELF_READY", copy.version());
            }
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(),
                    "副本状态已变化或尚未到可借业务日，保持人工处置中");
        }
        var txVersion = store.transitionTransaction(tx.id(), "MANUAL_HANDLING", "LENT", tx.version());
        if (txVersion == null) {
            store.transitionCopy(bound, "LENT", alreadyBound ? "RESERVED" : "SHELF_READY", copyVersion);
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "事务状态已变化，保持人工处置中");
        }
        if (!alreadyBound) {
            attachCopyToTx(tx.id(), bound);
        }
        var loanStarted = clock.today();
        var dueDate = loanStarted.plusDays(LOAN_DAYS);
        setTxDueDate(tx.id(), dueDate);
        store.setLoanStartedDate(tx.id(), loanStarted);
        return ModuleResult.Accepted.of(new Transaction(TxId.of(tx.id()), "RELEASED", "LENT"));
    }

    private ModuleResult<Transaction> confirmEntryFromManual(CirculationStore.TxRow tx) {
        if (tx.copyId() != null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(),
                    "事务已有绑定副本，请使用释放动作完成借出");
        }
        var bound = bindFirstAvailable(tx.titleId());
        if (bound == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "无可用副本，保持人工处置中");
        }
        // bindFirstAvailable 已把副本迁到 RESERVED（确认到馆 = 绑定保留）
        var txVersion = store.transitionTransaction(tx.id(), "MANUAL_HANDLING", "CONFIRMED", tx.version());
        if (txVersion == null) {
            store.transitionCopy(bound, "RESERVED", "SHELF_READY", versionOfCopy(bound));
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "事务状态已变化，保持人工处置中");
        }
        attachCopyToTx(tx.id(), bound);
        return ModuleResult.Accepted.of(new Transaction(TxId.of(tx.id()), "ENTRY_CONFIRMED", "CONFIRMED"));
    }

    private ModuleResult<Transaction> rejectFromManual(CirculationStore.TxRow tx) {
        var updated = store.transitionTransaction(tx.id(), "MANUAL_HANDLING", "NOT_BORROWED", tx.version());
        if (updated == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "事务状态已变化");
        }
        releaseBoundCopy(tx);
        return ModuleResult.Accepted.of(new Transaction(TxId.of(tx.id()), "REJECTED", "NOT_BORROWED"));
    }

    @Override
    public ModuleResult<Renewal> renew(RenewalCommand command) {
        var tx = store.txById(command.txId().value()).orElse(null);
        if (tx == null || !"LENT".equals(tx.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "借阅不在借出状态");
        }
        if (tx.readerId() != command.actor().accountId().value()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "仅本人可续借");
        }
        var base = tx.dueDate() == null ? clock.today() : tx.dueDate();
        var loanStarted = store.loanStartedDate(tx.id());
        if (loanStarted == null) {
            loanStarted = tx.reservationDate();
        }
        if (loanStarted == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "缺少借阅起始日期");
        }
        var requested = command.requestedDueDate() == null ? null : command.requestedDueDate().value();
        if (requested != null && !requested.isAfter(base)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "新到期日必须晚于当前到期日");
        }
        var maxDue = minDate(base.plusDays(RENEWAL_DAYS),
                loanStarted.plusDays(MAX_TOTAL_LOAN_DAYS));
        var earliestReservation = store.earliestValidReservationDateForTitle(tx.titleId());
        if (earliestReservation.isPresent()) {
            maxDue = minDate(maxDue, earliestReservation.get().minusDays(1));
        }
        if (!maxDue.isAfter(base)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(),
                    "借阅已达到期限上限或有效预约日前一日");
        }
        var newDue = requested == null ? maxDue : minDate(requested, maxDue);
        var updated = store.renewTransaction(tx.id(), newDue, tx.version());
        if (updated == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), tx.version(),
                    "事务状态已变化，请重新确认");
        }
        return ModuleResult.Accepted.of(new Renewal(command.txId(), BusinessDate.of(newDue), "RENEWED"));
    }

    // ---- 归还 -----------------------------------------------------------------

    @Override
    public ModuleResult<ReturnedCopy> returnCopy(ReturnCommand command) {
        var tx = store.openLentByCopy(command.copyId().value()).orElse(null);
        if (tx == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_006.code(), "重复归还或副本不在借出状态");
        }
        var copy = store.copyById(command.copyId().value()).orElse(null);
        if (copy == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "副本不存在");
        }
        var condition = command.condition() == null ? "INTACT" : command.condition().toUpperCase(Locale.ROOT);
        var copyNext = switch (condition) {
            case "INTACT" -> "PENDING_SHELVING";
            case "DAMAGED" -> "MAINTENANCE";
            case "LOST" -> "REMOVED";
            default -> null;
        };
        if (copyNext == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "未知归还品相");
        }
        var txNext = "INTACT".equals(condition) ? "RETURNED" : "RETURN_ABNORMAL";
        var txUpdated = store.transitionTransaction(tx.id(), "LENT", txNext, tx.version());
        if (txUpdated == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_006.code(), tx.version(),
                    "事务状态已变化（可能已归还）");
        }
        var copyUpdated = "INTACT".equals(condition)
                ? store.transitionCopyAndSetLoanEligibleOn(copy.id(), "LENT", copyNext,
                        copy.version(), clock.today().plusDays(1))
                : store.transitionCopy(copy.id(), "LENT", copyNext, copy.version());
        if (copyUpdated == null) {
            // 回滚事务迁移，保持整体一致（同一应用事务内，最终由 UnitOfWork 收口）
            store.transitionTransaction(tx.id(), txNext, "LENT", txUpdated);
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_006.code(), copy.version(),
                    "副本状态已变化，请重新确认");
        }
        return ModuleResult.Accepted.of(new ReturnedCopy(TxId.of(tx.id()), txNext, command.copyId(),
                tx.readerId(), TitleId.of(tx.titleId()), tx.dueDate()));
    }

    // ---- 副本维护 ----------------------------------------------------------------

    @Override
    public ModuleResult<CopySnapshot> createCopy(CreateCopy command) {
        if (command.copyNo() == null || command.copyNo().isBlank() || command.copyNo().length() > 32) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "copyNo 必填且不超过 32 字符");
        }
        if (store.copyNoExists(command.titleId().value(), command.copyNo())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_010.code(), "同书目下副本号已存在");
        }
        var id = store.insertCopy(command.titleId().value(), command.copyNo(), clock.today().plusDays(1));
        return ModuleResult.Accepted.of(new CopySnapshot(CopyId.of(id), command.titleId(),
                command.copyNo(), "PENDING_SHELVING", 0));
    }

    @Override
    public ModuleResult<CopyPage> listCopies(CopyCriteria criteria) {
        if (criteria.status() != null && !API_TO_DB_STATUS.containsKey(criteria.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(), "副本状态筛选值无效");
        }
        var cursor = criteria.page() == null ? PageCursor.first() : criteria.page();
        var afterId = cursor.cursor() == null ? 0 : Long.parseLong(cursor.cursor());
        var dbStatus = criteria.status() == null ? null : API_TO_DB_STATUS.get(criteria.status());
        var rows = store.listCopies(criteria.titleId() == null ? null : criteria.titleId().value(),
                dbStatus, afterId, cursor.limit() + 1);
        var hasNext = rows.size() > cursor.limit();
        var pageRows = rows.stream().limit(cursor.limit()).toList();
        var items = pageRows.stream().map(this::toSnapshot).toList();
        var nextCursor = hasNext && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).id()) : null;
        return ModuleResult.Accepted.of(new CopyPage(items, nextCursor));
    }

    @Override
    public ModuleResult<CopySnapshot> maintainCopy(MaintainCopy command) {
        var copy = store.copyById(command.copyId().value()).orElse(null);
        if (copy == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "副本不存在");
        }
        var action = command.action() == null ? "" : command.action().toUpperCase(Locale.ROOT);
        var transition = switch (action) {
            case "SHELVE" -> Map.entry("PENDING_SHELVING", "SHELF_READY");
            case "MAINTAIN" -> Map.entry("SHELF_READY", "MAINTENANCE");
            case "RESTORE" -> Map.entry("MAINTENANCE", "SHELF_READY");
            case "REMOVE" -> Map.entry("SHELF_READY", "REMOVED");
            default -> null;
        };
        if (transition == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_003.code(), "未知副本维护动作");
        }
        if (!transition.getKey().equals(copy.status())) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), copy.version(),
                    "当前状态不允许该迁移（" + DB_TO_API_STATUS.get(copy.status()) + "）");
        }
        var updated = "RESTORE".equals(action)
                ? store.transitionCopyAndSetLoanEligibleOn(copy.id(), transition.getKey(), transition.getValue(),
                        command.version(), clock.today())
                : store.transitionCopy(copy.id(), transition.getKey(), transition.getValue(), command.version());
        if (updated == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CIRC_003.code(), copy.version(),
                    "版本或状态已变化，请基于最新版本重新提交");
        }
        if ("SHELVE".equals(action)) {
            refreshSettlement(copy.titleId(), command.actor());
        }
        return ModuleResult.Accepted.of(new CopySnapshot(CopyId.of(copy.id()), TitleId.of(copy.titleId()),
                copy.copyNo(), DB_TO_API_STATUS.get(transition.getValue()), updated));
    }

    /**
     * 维护完成刷新结算（AT-0002 §1.28「书目出现可用副本时…维护完成触发的即时结算」）：
     * 候补队头绑定一个可用副本并置 CONFIRMED，登记 WAITLIST_ASSIGNED。**仅分配、不清退**。
     * 无可分配副本时不做任何改动，留待闭馆结算按到期清退。
     */
    private void refreshSettlement(long titleId, ActorContext actor) {
        var head = store.waitlistByTitle(titleId, 1);
        if (head.isEmpty()) {
            return;
        }
        var candidate = head.getFirst();
        if (candidate.reservationDate() == null) {
            return;
        }
        var copyId = bindFirstAvailable(titleId);
        if (copyId == null) {
            return;
        }
        if (store.transitionTransaction(candidate.id(), "WAITLIST", "CONFIRMED",
                candidate.version()) == null) {
            // 事务状态已变化：释放刚绑定的副本，回到可借
            store.transitionCopy(copyId, "RESERVED", "SHELF_READY", versionOfCopy(copyId));
            return;
        }
        attachCopyToTx(candidate.id(), copyId);
        outbox.append("CIRCULATION_TX", candidate.id(), "WAITLIST_ASSIGNED",
                Map.of("readerId", candidate.readerId(),
                        "reservationDate", candidate.reservationDate().toString()));
        audit.appendWithBusinessFact(AuditEventTypes.WAITLIST_ASSIGNED,
                actor == null ? null : actor.accountId().value(),
                new BusinessOutcome(candidate.id(), "ASSIGNED"));
    }

    // ---- 内部工具 ----------------------------------------------------------------

    /** CAS 绑定第一个可用副本，返回副本 id；竞争全部失败返回 null（X1）。 */
    private Long bindFirstAvailable(long titleId) {
        for (var copy : store.availableCopies(titleId, 8)) {
            if (store.transitionCopy(copy.id(), "SHELF_READY", "RESERVED", copy.version()) != null) {
                return copy.id();
            }
        }
        return null;
    }

    /** Immediate manual release may bind only a copy whose lending date has arrived. */
    private Long bindFirstLoanable(long titleId) {
        for (var copy : store.loanableCopies(titleId, clock.today(), 8)) {
            if (store.transitionCopy(copy.id(), "SHELF_READY", "RESERVED", copy.version()) != null) {
                return copy.id();
            }
        }
        return null;
    }

    private long versionOfCopy(long copyId) {
        return store.copyById(copyId).map(CirculationStore.CopyRow::version).orElse(0L);
    }

    /** 人工处置借出/确认后补绑副本（保留事务状态与版本计数不变）。 */
    private void attachCopyToTx(long txId, Long copyId) {
        store.attachCopy(txId, copyId);
    }

    private void setTxDueDate(long txId, LocalDate dueDate) {
        store.setDueDate(txId, dueDate);
    }

    private void releaseBoundCopy(CirculationStore.TxRow tx) {
        if (tx.copyId() == null) {
            return;
        }
        store.copyById(tx.copyId()).ifPresent(copy -> {
            if ("RESERVED".equals(copy.status())) {
                store.transitionCopyAndSetLoanEligibleOn(copy.id(), "RESERVED", "PENDING_SHELVING",
                        copy.version(), clock.today().plusDays(1));
            }
        });
    }

    private CopyCommands.CopySnapshot toSnapshot(CirculationStore.CopyRow row) {
        return new CopyCommands.CopySnapshot(CopyId.of(row.id()), TitleId.of(row.titleId()),
                row.copyNo(), DB_TO_API_STATUS.get(row.status()), row.version());
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
