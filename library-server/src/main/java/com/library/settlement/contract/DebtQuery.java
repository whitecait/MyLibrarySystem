package com.library.settlement.contract;

import com.library.contract.ModuleResult;

/**
 * SETTLEMENT 欠费快照查询能力（MLS-DOC-MD-0004 §2）。
 * CIRCULATION 只读本快照判定续借/借阅资格，不直写罚款或订单。
 */
public interface DebtQuery {

    /** @param dueFineCount 当前 DUE（未缴）罚款笔数；dueCents 为未缴合计。 */
    ModuleResult<DebtSnapshot> debtFor(com.library.identity.contract.model.ReaderId readerId);

    record DebtSnapshot(long readerId, int dueFineCount, long dueCents) {

        public boolean hasDebt() {
            return dueFineCount > 0;
        }
    }
}
