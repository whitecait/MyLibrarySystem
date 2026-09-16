package com.library.circulation.contract;

/** 借阅事务结果摘要（OpenAPI TransactionData：txId/outcome/status）。 */
public record Transaction(com.library.circulation.contract.model.TxId txId,
                          String outcome,
                          String status) {
}
