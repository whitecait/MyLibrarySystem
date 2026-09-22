package com.library.application.returning;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
import com.library.contract.error.StableErrorCode;
import com.library.contract.outbox.OutboxAppender;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.catalog.contract.TitleQuery;
import com.library.catalog.contract.TitleView;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.ReturnCommands.ReturnedCopy;
import com.library.identity.contract.AuthorizationCheck;
import com.library.settlement.contract.FineCommands;
import com.library.settlement.contract.model.MoneyCents;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 归还应用用例（MLS-DOC-MD-0004 §3/§4）：流通迁移、罚款形成、Outbox 登记
 * 在同一事务内整体提交或回滚（IT-CIRC-003；归还＝借阅结束＋副本迁移＋罚款＋事件）。
 * 成功路径审计（DEF-M6-011）与事件登记同点写入；detail 携带 relatedId（罚款用 fineId、
 * 归还用 txId）与 outcome。
 * 罚款规则（待确认业务参数，批次报告登记）：LOST/DAMAGED 按书价全额；
 * INTACT 逾期按 100 分/日、封顶书价（BR-0017）。
 * TitleQuery 注入为 §3 表按需补全（罚款封顶基数需书目价格）。
 */
public interface ReturnUseCases {

    /** 应用层结果：returned 为模块结果（HTTP 只映射 ReturnData 字段），fineId 为同事务罚款结果。 */
    ModuleResult<ReturnResult> returnCopy(ActorContext actor,
                                          ReturnCommands.ReturnCommand command);

    record ReturnResult(ReturnedCopy returned, Long fineId) {
    }
}

@Component
record ReturnUseCasesImpl(AuthorizationCheck authorization,
                          ReturnCommands returns,
                          FineCommands fines,
                          TitleQuery titleQuery,
                          OutboxAppender outbox,
                          AuditAppender audit) implements ReturnUseCases {

    private static final long OVERDUE_FINE_PER_DAY = 100;

    @Override
    public ModuleResult<ReturnResult> returnCopy(ActorContext actor,
                                                 ReturnCommands.ReturnCommand command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var result = returns.returnCopy(command);
        if (result instanceof ModuleResult.Accepted<ReturnedCopy> accepted) {
            var returned = accepted.value();
            var amount = fineAmountOf(command, returned);
            Long fineId = null;
            if (amount > 0) {
                // 同事务罚款形成：同归还至多一笔（模块层按 source_tx_id 幂等吸收，BR-0019）
                var fine = fines.createFine(new FineCommands.FineFromReturn(
                        returned.txId().value(), returned.readerId(),
                        MoneyCents.of(amount), "归还形成：" + conditionOf(command)));
                if (fine instanceof ModuleResult.Accepted<FineCommands.Fine> fineAccepted) {
                    fineId = fineAccepted.value().fineId().value();
                    outbox.append("SETTLEMENT_FINE", fineId, "FINE_CREATED",
                            java.util.Map.of("sourceTxId", returned.txId().value(),
                                    "amountCents", amount, "readerId", returned.readerId()));
                    audit.appendWithBusinessFact(AuditEventTypes.FINE_CREATED,
                            actor.accountId().value(), new BusinessOutcome(fineId, "CREATED"));
                }
            }
            outbox.append("CIRCULATION_TX", returned.txId().value(), "COPY_RETURNED",
                    java.util.Map.of("copyId", command.copyId().value(),
                            "condition", conditionOf(command), "readerId", returned.readerId()));
            audit.appendWithBusinessFact(AuditEventTypes.COPY_RETURNED, actor.accountId().value(),
                    new BusinessOutcome(returned.txId().value(), returned.outcome()));
            return ModuleResult.Accepted.of(new ReturnResult(returned, fineId));
        }
        if (result instanceof ModuleResult.Rejected<ReturnedCopy> moduleRejected) {
            return ModuleResult.Rejected.propagate(moduleRejected);
        }
        throw new IllegalStateException("未知 ModuleResult 形态");
    }

    /**
     * 罚款金额：LOST/DAMAGED = 书价全额；INTACT = 逾期天数×100 封顶书价；
     * 无逾期且品相完好不计罚。
     */
    private long fineAmountOf(ReturnCommands.ReturnCommand command, ReturnedCopy returned) {
        var condition = conditionOf(command);
        var priceCents = titlePriceOf(returned.titleId());
        if (priceCents < 0) {
            return 0;
        }
        return switch (condition) {
            case "LOST", "DAMAGED" -> priceCents;
            case "INTACT" -> {
                var due = returned.dueDate();
                if (due == null) {
                    yield 0;
                }
                var overdueDays = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now()));
                yield Math.min(overdueDays * OVERDUE_FINE_PER_DAY, priceCents);
            }
            default -> 0;
        };
    }

    /** 罚款封顶基数（BR-0017）；书目不可见时返回 -1 表示不计罚。 */
    private long titlePriceOf(TitleId titleId) {
        if (titleQuery.title(titleId, com.library.contract.security.Visibility.PUBLIC)
                instanceof ModuleResult.Accepted<TitleView> accepted) {
            return accepted.value().priceCents();
        }
        return -1;
    }

    private String conditionOf(ReturnCommands.ReturnCommand command) {
        return command.condition() == null ? "INTACT" : command.condition().toUpperCase(Locale.ROOT);
    }

    private ModuleResult.Rejected<AuthorizationDecision> authorizeOrReject(ActorContext actor) {
        if (authorization.authorize(actor, Capability.LIBRARIAN, SubjectRef.system())
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }
}
