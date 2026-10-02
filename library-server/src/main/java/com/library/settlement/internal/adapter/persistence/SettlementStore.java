package com.library.settlement.internal.adapter.persistence;

import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SETTLEMENT 持久化内部缝（MLS-DOC-MD-0004 §4）：罚款来源唯一（BR-0019）、
 * 订单独占、回调事件去重、终态吸收。B2 仅罚款形成与欠费读取。
 */
public interface SettlementStore {

    Optional<FineRow> fineBySourceTx(long sourceTxId);

    long insertFine(NewFine candidate);

    Optional<DebtSummary> debtFor(long readerId);

    Optional<FineRow> fineById(long fineId);
    List<FineRow> listFines(Long readerId, String status, long afterId, int limit);
    List<OrderRow> listOccupiedOrders(long readerId, long afterId, int limit);
    Optional<OrderRow> orderById(long orderId);
    Optional<OrderRow> lockOrderById(long orderId);
    void lockCallbackEvent(String eventId);
    Optional<OrderRow> unreleasedOrder(long fineId);
    long insertOrder(long fineId, long amountCents, Instant expiresAt);
    boolean transitionOrder(long orderId, String expected, String next, Instant at);
    Optional<CallbackRow> callback(String eventId);
    void insertCallback(CallbackRow callback);
    void settleFine(long fineId, Instant at);
    long insertRefund(long orderId, long amountCents, long reviewId);
    Optional<ReconciliationRow> reconciliation(LocalDate date);
    long insertReconciliation(LocalDate date, Map<String, Integer> counts, List<String> exceptions);

    record FineRow(long id, long readerId, long sourceTxId, long amountCents, String status, Instant createdAt) {
    }

    record NewFine(long readerId, long sourceTxId, long amountCents, String reason) {
    }

    record DebtSummary(int dueFineCount, long dueCents) {
    }

    record OrderRow(long id, long fineId, long amountCents, String status, Instant expiresAt, long version) {}
    record CallbackRow(String eventId, long orderId, String result, Instant occurredAt,
                       String outcome) {}
    record ReconciliationRow(long id, LocalDate businessDate, Map<String, Integer> counts,
                             List<String> exceptions) {}
}
