package com.library.application.loan;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.Role;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.contract.time.BusinessClock;
import com.library.contract.transaction.UnitOfWork;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.ReaderCirculationQuery;
import com.library.circulation.contract.Transaction;
import com.library.identity.contract.AuthorizationCheck;
import com.library.identity.contract.ReaderQuery;
import com.library.identity.contract.ReaderSnapshot;
import com.library.identity.contract.model.ReaderId;
import com.library.settlement.contract.DebtQuery;
import org.springframework.stereotype.Component;

/**
 * 借出应用用例（MLS-DOC-MD-0004 §3）：预约、现场借阅、取书、续借、人工处置。
 * 提交时重验读者资格（READER 且 ACTIVE），资格拒绝 MLS-CIRC-011；
 * 事件登记与业务写入同一事务（事件随 UnitOfWork 提交/回滚）。
 * 成功路径审计（DEF-M6-011）与事件登记同点写入，detail 携带 relatedId 与 outcome。
 * AuthorizationCheck/DebtQuery/ReaderQuery 为 §3 表的按需补全（批次报告登记）。
 */
public interface LoanUseCases {

    ModuleResult<ReservationCommands.Reservation> reserve(ActorContext actor,
                                                          ReservationCommands.ReservationCommand command);

    ModuleResult<Transaction> cancel(ActorContext actor, ReservationCommands.CancelReservation command);

    ModuleResult<LoanCommands.LoanBatch> lend(ActorContext actor, LoanCommands.WalkInLoanCommand command);

    ModuleResult<Transaction> claim(ActorContext actor, LoanCommands.ClaimPickup command);

    ModuleResult<Transaction> applyManualAction(ActorContext actor, LoanCommands.ManualAction command);

    ModuleResult<LoanCommands.Renewal> renew(ActorContext actor, LoanCommands.RenewalCommand command);
}

@Component
record LoanUseCasesImpl(AuthorizationCheck authorization,
                        ReaderQuery readerQuery,
                        DebtQuery debtQuery,
                        ReaderCirculationQuery circulationQuery,
                        BusinessClock clock,
                        UnitOfWork unitOfWork,
                        ReservationCommands reservations,
                        LoanCommands loans,
                        OutboxAppender outbox,
                        AuditAppender audit) implements LoanUseCases {

    private static final String TX_AGG_TYPE = "CIRCULATION_TX";

    @Override
    public ModuleResult<ReservationCommands.Reservation> reserve(
            ActorContext actor, ReservationCommands.ReservationCommand command) {
        return unitOfWork.inTransaction(() -> reserveInTransaction(actor, command));
    }

    private ModuleResult<ReservationCommands.Reservation> reserveInTransaction(
            ActorContext actor, ReservationCommands.ReservationCommand command) {
        var readerId = resolveReaderId(actor, command.readerId());
        circulationQuery.lockReader(readerId);
        var rejected = readerEligibilityRejected(readerId);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var authorized = authorizeReaderOrLibrarian(actor, readerId);
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        circulationQuery.lockTitleReservations(command.titleId().value());
        if (circulationQuery.activity(readerId, clock.today()).activeReservationCount()
                >= ReservationCommands.MAX_ACTIVE_RESERVATIONS) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "读者已达到五项活动预约上限");
        }
        var result = reservations.reserve(new ReservationCommands.ReservationCommand(
                readerId, command.titleId(), command.reservationDate(), command.pickupSlot(),
                command.channel(), command.confirmWaitlist(), actor));
        appendTxEvent(result, AuditEventTypes.RESERVATION_DECIDED, readerId, actor);
        return result;
    }

    @Override
    public ModuleResult<Transaction> cancel(ActorContext actor,
                                            ReservationCommands.CancelReservation command) {
        return unitOfWork.inTransaction(() -> cancelInTransaction(actor, command));
    }

    private ModuleResult<Transaction> cancelInTransaction(ActorContext actor,
                                                          ReservationCommands.CancelReservation command) {
        var authorized = authorizeReaderOrLibrarian(actor, actor.accountId().value());
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        var result = reservations.cancel(command);
        appendTxEvent(result, AuditEventTypes.RESERVATION_CANCELLED, null, actor);
        return result;
    }

    @Override
    public ModuleResult<LoanCommands.LoanBatch> lend(ActorContext actor,
                                                     LoanCommands.WalkInLoanCommand command) {
        return unitOfWork.inTransaction(() -> lendInTransaction(actor, command));
    }

    private ModuleResult<LoanCommands.LoanBatch> lendInTransaction(
            ActorContext actor, LoanCommands.WalkInLoanCommand command) {
        var authorized = authorizeOrReject(actor, Capability.LIBRARIAN, SubjectRef.system());
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        if (!"DESK".equalsIgnoreCase(command.channel())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_001.code(),
                    "现场借出仅支持馆员到台办理");
        }
        var readerId = resolveReaderId(actor, command.readerId());
        circulationQuery.lockReader(readerId);
        var rejected = readerEligibilityRejected(readerId);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var activity = circulationQuery.activity(readerId, clock.today());
        if (activity.hasOverdueLoan()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "存在逾期未还副本，暂不能借阅");
        }
        var debt = debtQuery.debtFor(ReaderId.of(readerId));
        if (!(debt instanceof ModuleResult.Accepted<DebtQuery.DebtSnapshot> acceptedDebt)
                || acceptedDebt.value().hasDebt()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "存在未缴罚款或无法核验欠费，暂不能借阅");
        }
        var result = loans.lend(new LoanCommands.WalkInLoanCommand(
                readerId, command.items(), command.channel(), actor));
        appendTxEvent(result, AuditEventTypes.WALKIN_LOAN_BATCH, readerId, actor);
        appendLoanBatchAudit(result, actor);
        return result;
    }

    @Override
    public ModuleResult<Transaction> claim(ActorContext actor, LoanCommands.ClaimPickup command) {
        return unitOfWork.inTransaction(() -> claimInTransaction(actor, command));
    }

    private ModuleResult<Transaction> claimInTransaction(ActorContext actor, LoanCommands.ClaimPickup command) {
        var readerId = circulationQuery.readerForTransaction(command.txId().value()).orElse(0);
        if (readerId <= 0) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_004.code(), "预约读者不可见");
        }
        circulationQuery.lockReader(readerId);
        var eligibility = readerEligibilityRejected(readerId);
        if (eligibility != null) {
            return ModuleResult.Rejected.propagate(eligibility);
        }
        if (readerCannotTakeAdditionalLoan(readerId)) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "当前存在逾期未还或未缴罚款，暂不能取书");
        }
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN, SubjectRef.system());
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var result = loans.claim(command);
        var routedToManual = result instanceof ModuleResult.Accepted<?> accepted
                && accepted.value() instanceof Transaction transaction
                && "MANUAL_HANDLING".equals(transaction.status());
        var eventType = routedToManual ? AuditEventTypes.RESERVATION_DECIDED : AuditEventTypes.PICKUP_CLAIMED;
        appendTxEvent(result, eventType, routedToManual ? readerId : null, actor);
        return result;
    }

    @Override
    public ModuleResult<Transaction> applyManualAction(ActorContext actor,
                                                       LoanCommands.ManualAction command) {
        return unitOfWork.inTransaction(() -> applyManualActionInTransaction(actor, command));
    }

    private ModuleResult<Transaction> applyManualActionInTransaction(ActorContext actor,
                                                                     LoanCommands.ManualAction command) {
        var action = command.action() == null ? "" : command.action().toUpperCase(java.util.Locale.ROOT);
        if ("RELEASE".equals(action)) {
            var readerId = circulationQuery.readerForTransaction(command.txId().value()).orElse(0);
            if (readerId <= 0) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_005.code(), "处置读者不可见");
            }
            circulationQuery.lockReader(readerId);
            var eligibility = readerEligibilityRejected(readerId);
            if (eligibility != null || readerCannotTakeAdditionalLoan(readerId)) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "当前存在逾期未还、未缴罚款或账户不可用");
            }
        }
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN, SubjectRef.system());
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var result = loans.applyManualAction(command);
        appendTxEvent(result, AuditEventTypes.MANUAL_ACTION_APPLIED, null, actor);
        return result;
    }

    @Override
    public ModuleResult<LoanCommands.Renewal> renew(ActorContext actor,
                                                    LoanCommands.RenewalCommand command) {
        return unitOfWork.inTransaction(() -> renewInTransaction(actor, command));
    }

    private ModuleResult<LoanCommands.Renewal> renewInTransaction(
            ActorContext actor, LoanCommands.RenewalCommand command) {
        var txReaderId = circulationQuery.readerForTransaction(command.txId().value()).orElse(0);
        circulationQuery.lockReader(actor.accountId().value());
        var rejected = authorizeOrReject(actor, Capability.READER_SELF,
                SubjectRef.account(actor.accountId().asString()));
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        if (txReaderId == actor.accountId().value()) {
            circulationQuery.titleForTransaction(command.txId().value())
                    .ifPresent(circulationQuery::lockTitleReservations);
        }
        if (readerHasDebtOrOverdueLoan(actor.accountId().value())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "存在逾期未还副本，暂不能续借");
        }
        var result = loans.renew(command);
        appendTxEvent(result, AuditEventTypes.LOAN_RENEWED, null, actor);
        return result;
    }

    /** READER 本人或 LIBRARIAN 代办（onBehalfOf；channel/readerId 字段不是凭据，MD-0003 §1）。 */
    private long resolveReaderId(ActorContext actor, long requestedReaderId) {
        if (actor.role() == Role.LIBRARIAN && requestedReaderId > 0) {
            return requestedReaderId;
        }
        return actor.accountId().value();
    }

    /** 读者资格：READER 且 ACTIVE（RA-0008 资格守卫）；拒绝 MLS-CIRC-011。 */
    private ModuleResult.Rejected<ReservationCommands.Reservation> readerEligibilityRejected(long readerId) {
        if (readerQuery.reader(ReaderId.of(readerId)) instanceof ModuleResult.Accepted<ReaderSnapshot> accepted) {
            if ("ACTIVE".equals(accepted.value().status())) {
                return null;
            }
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "读者账户未处于 ACTIVE 状态");
        }
        return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_011.code(), "读者不存在或不可见");
    }

    private boolean readerCannotTakeAdditionalLoan(long readerId) {
        var activity = circulationQuery.activity(readerId, clock.today());
        return activity.activeLoanCount() >= LoanCommands.MAX_ACTIVE_LOANS
                || activity.hasOverdueLoan() || hasUnpaidOrUnverifiableDebt(readerId);
    }

    private boolean readerHasDebtOrOverdueLoan(long readerId) {
        return circulationQuery.activity(readerId, clock.today()).hasOverdueLoan()
                || hasUnpaidOrUnverifiableDebt(readerId);
    }

    private boolean hasUnpaidOrUnverifiableDebt(long readerId) {
        var debt = debtQuery.debtFor(ReaderId.of(readerId));
        return !(debt instanceof ModuleResult.Accepted<DebtQuery.DebtSnapshot> accepted)
                || accepted.value().hasDebt();
    }

    private ModuleResult.Rejected<AuthorizationDecision> authorizeReaderOrLibrarian(ActorContext actor,
                                                                                    long subjectReaderId) {
        if (authorization.authorize(actor, Capability.READER_SELF_OR_LIBRARIAN,
                SubjectRef.account(String.valueOf(subjectReaderId)))
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }

    private ModuleResult.Rejected<AuthorizationDecision> authorizeOrReject(ActorContext actor,
                                                                           Capability capability,
                                                                           SubjectRef subject) {
        if (authorization.authorize(actor, capability, subject)
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }

    /** 事件登记与成功审计（同一事务；仅 Accepted 且携带 txId 的事实登记）。 */
    private void appendTxEvent(ModuleResult<?> result, String eventType, Long readerId,
                               ActorContext actor) {
        if (!(result instanceof ModuleResult.Accepted<?> accepted)) {
            return;
        }
        var aggId = extractTxId(accepted.value());
        if (aggId > 0) {
            outbox.append(TX_AGG_TYPE, aggId, eventType,
                    java.util.Map.of("readerId", readerId == null ? 0 : readerId));
            audit.appendWithBusinessFact(eventType, actor.accountId().value(),
                    new BusinessOutcome(aggId, extractOutcome(accepted.value())));
        }
    }

    /** 现场借阅批次无聚合行（LoanBatch），按册登记成功审计（每成功册一条）。 */
    private void appendLoanBatchAudit(ModuleResult<LoanCommands.LoanBatch> result, ActorContext actor) {
        if (!(result instanceof ModuleResult.Accepted<LoanCommands.LoanBatch> accepted)) {
            return;
        }
        for (var item : accepted.value().items()) {
            if (item.txId() == null || !"LENT".equals(item.outcome())) {
                continue;
            }
            audit.appendWithBusinessFact(AuditEventTypes.WALKIN_LOAN_BATCH, actor.accountId().value(),
                    new BusinessOutcome(item.txId().value(), item.outcome()));
        }
    }

    /** 结果记录的业务结果字段（Reservation/Transaction/Renewal 均以 outcome 表达）。 */
    private String extractOutcome(Object value) {
        try {
            var outcome = value.getClass().getMethod("outcome").invoke(value);
            return outcome == null ? "OK" : outcome.toString();
        } catch (Exception reflectiveFailure) {
            return "OK";
        }
    }

    private long extractTxId(Object value) {
        try {
            var txId = value.getClass().getMethod("txId").invoke(value);
            if (txId == null) {
                return 0;
            }
            return (long) txId.getClass().getMethod("value").invoke(txId);
        } catch (Exception reflectiveFailure) {
            return 0;
        }
    }
}
