package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;

import java.util.List;

/**
 * CIRCULATION 借出能力：现场逐册借阅、到台取书、人工处置、续借。
 * 每册独立事务、混合批次逐册呈现（MD-0003 §3）；续借拒绝 MLS-CIRC-012；
 * 人工处置守卫不满足 MLS-CIRC-005；无效取书对象 MLS-CIRC-004。
 */
public interface LoanCommands {

    /** 现场逐册借阅：每册独立判定与提交，混合结果保留。 */
    ModuleResult<LoanBatch> lend(WalkInLoanCommand command);

    /** 到台取书借出：CONFIRMED 预约 + 绑定副本 → LENT；提交时重验（X4）。 */
    ModuleResult<Transaction> claim(ClaimPickup command);

    /** 人工处置：RELEASE 绑副本借出 / CONFIRM_ENTRY 确认到馆 / REJECT 终态。 */
    ModuleResult<Transaction> applyManualAction(ManualAction command);

    /** 续借：无候补等待、次数上限、无欠费；拒绝 MLS-CIRC-012(422)。 */
    ModuleResult<Renewal> renew(RenewalCommand command);

    record WalkInItem(TitleId titleId, CopyId copyId) {
    }

    record WalkInLoanCommand(long readerId,
                             List<WalkInItem> items,
                             String channel,
                             ActorContext actor) {
    }

    /** 逐册结果（OpenAPI LoanBatchItem：itemRef 为请求内序号 1..n）。 */
    record LoanBatchItem(String itemRef, TxId txId, String outcome, List<String> reasons) {
    }

    record LoanBatch(List<LoanBatchItem> items) {
    }

    record ClaimPickup(TxId txId, CopyId copyId, ActorContext actor) {
    }

    record ManualAction(TxId txId, String action, String reason, ActorContext actor) {
    }

    record RenewalCommand(TxId txId, BusinessDate requestedDueDate, ActorContext actor) {
    }

    /** 续借结果（OpenAPI RenewalData：txId/dueDate/outcome）。 */
    record Renewal(TxId txId, BusinessDate dueDate, String outcome) {
    }
}
