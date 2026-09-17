package com.library.delivery.contract;

import com.library.contract.ModuleResult;
import java.time.Instant;
import java.util.Map;

public interface OutboxConsumer {
    ModuleResult<DeliveryReceipt> consume(String consumerId, OutboxEnvelope event);
    record OutboxEnvelope(long eventId,String aggregateType,long aggregateId,String eventType,Map<String,Object> payload,Instant createdAt) {}
    record DeliveryReceipt(long eventId,String consumerId,boolean duplicate) {}
}
