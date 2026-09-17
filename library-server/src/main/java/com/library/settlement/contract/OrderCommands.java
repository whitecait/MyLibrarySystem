package com.library.settlement.contract;

import com.library.contract.ModuleResult;
import com.library.settlement.contract.model.FineId;

import java.time.Instant;

public interface OrderCommands {
    ModuleResult<Order> createOrder(CreateOrder command);
    ModuleResult<Callback> applyCallback(PaymentCallback command);
    ModuleResult<Order> closeOrder(CloseOrder command);

    record CreateOrder(FineId fineId, long readerId) {}
    record PaymentCallback(String eventId, long orderId, String result, Instant occurredAt) {}
    record CloseOrder(long orderId) {}
    record Order(long orderId, FineId fineId, long amountCents, String status, Instant expiresAt, long version) {}
    record Callback(String eventId, long orderId, String outcome) {}
}
