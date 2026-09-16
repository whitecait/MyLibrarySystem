package com.library.settlement.internal.adapter.persistence;

import java.util.Optional;

/**
 * SETTLEMENT 持久化内部缝（MLS-DOC-MD-0004 §4）：罚款来源唯一（BR-0019）、
 * 订单独占、回调事件去重、终态吸收。B2 仅罚款形成与欠费读取。
 */
public interface SettlementStore {

    Optional<FineRow> fineBySourceTx(long sourceTxId);

    long insertFine(NewFine candidate);

    Optional<DebtSummary> debtFor(long readerId);

    record FineRow(long id, long readerId, long sourceTxId, long amountCents, String status) {
    }

    record NewFine(long readerId, long sourceTxId, long amountCents, String reason) {
    }

    record DebtSummary(int dueFineCount, long dueCents) {
    }
}
