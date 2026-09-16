package com.library.application.loan;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.Role;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
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
                        ReservationCommands reservations,
                        LoanCommands loans,
                        OutboxAppender outbox) implements LoanUseCases {

    private static final String TX_AGG_TYPE = "CIRCULATION_TX";

    @Override
    public ModuleResult<ReservationCommands.Reservation> reserve(
            ActorContext actor, ReservationCommands.ReservationCommand command) {
        var readerId = resolveReaderId(actor, command.readerId());
        var rejected = readerEligibilityRejected(readerId);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var authorized = authorizeReaderOrLibrarian(actor, readerId);
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        var result = reservations.reserve(new ReservationCommands.ReservationCommand(
                readerId, command.titleId(), command.reservationDate(), command.pickupSlot(),
                command.channel(), command.confirmWaitlist(), actor));
        appendTxEvent(result, "RESERVATION_DECIDED", readerId);
        return result;
    }

    @Override
    public ModuleResult<Transaction> cancel(ActorContext actor,
                                            ReservationCommands.CancelReservation command) {
        var authorized = authorizeReaderOrLibrarian(actor, actor.accountId().value());
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        var result = reservations.cancel(command);
        appendTxEvent(result, "RESERVATION_CANCELLED", null);
        return result;
    }

    @Override
    public ModuleResult<LoanCommands.LoanBatch> lend(ActorContext actor,
                                                     LoanCommands.WalkInLoanCommand command) {
        var readerId = resolveReaderId(actor, command.readerId());
        var rejected = readerEligibilityRejected(readerId);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var authorized = authorizeReaderOrLibrarian(actor, readerId);
        if (authorized != null) {
            return ModuleResult.Rejected.propagate(authorized);
        }
        var result = loans.lend(new LoanCommands.WalkInLoanCommand(
                readerId, command.items(), command.channel(), actor));
        appendTxEvent(result, "WALKIN_LOAN_BATCH", readerId);
        return result;
    }

    @Override
    public ModuleResult<Transaction> claim(ActorContext actor, LoanCommands.ClaimPickup command) {
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN, SubjectRef.system());
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var result = loans.claim(command);
        appendTxEvent(result, "PICKUP_CLAIMED", null);
        return result;
    }

    @Override
    public ModuleResult<Transaction> applyManualAction(ActorContext actor,
                                                       LoanCommands.ManualAction command) {
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN, SubjectRef.system());
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var result = loans.applyManualAction(command);
        appendTxEvent(result, "MANUAL_ACTION_APPLIED", null);
        return result;
    }

    @Override
    public ModuleResult<LoanCommands.Renewal> renew(ActorContext actor,
                                                    LoanCommands.RenewalCommand command) {
        var rejected = authorizeOrReject(actor, Capability.READER_SELF,
                SubjectRef.account(actor.accountId().asString()));
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var debt = debtQuery.debtFor(ReaderId.of(actor.accountId().value()));
        if (debt instanceof ModuleResult.Accepted<DebtQuery.DebtSnapshot> accepted
                && accepted.value().hasDebt()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CIRC_012.code(), "存在未缴罚款，暂不能续借");
        }
        var result = loans.renew(command);
        appendTxEvent(result, "LOAN_RENEWED", null);
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

    /** 事件登记（同一事务；仅 Accepted 且携带 txId 的事实登记）。 */
    private void appendTxEvent(ModuleResult<?> result, String eventType, Long readerId) {
        if (!(result instanceof ModuleResult.Accepted<?> accepted)) {
            return;
        }
        var aggId = extractTxId(accepted.value());
        if (aggId > 0) {
            outbox.append(TX_AGG_TYPE, aggId, eventType,
                    java.util.Map.of("readerId", readerId == null ? 0 : readerId));
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
