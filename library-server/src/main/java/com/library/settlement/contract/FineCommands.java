package com.library.settlement.contract;

import com.library.contract.ModuleResult;

/**
 * SETTLEMENT 罚款形成能力（MLS-DOC-MD-0004 §2）。
 * 仅由归还应用用例在流通迁移同一事务内调用；同归还事务至多一笔罚款
 * （UNIQUE(source_tx_id)，BR-0019）。
 */
public interface FineCommands {

    ModuleResult<Fine> createFine(FineFromReturn command);

    /** 归还形成的罚款命令（sourceTxId 为归还对应的借阅事务）。 */
    record FineFromReturn(long sourceTxId,
                          long readerId,
                          com.library.settlement.contract.model.MoneyCents amount,
                          String reason) {
    }

    /** 罚款结果（OpenAPI FineData：status 为 API 枚举 DUE→UNPAID）。 */
    record Fine(com.library.settlement.contract.model.FineId fineId,
                long readerId,
                long amountCents,
                String status,
                java.time.Instant createdAt) {
    }
}
