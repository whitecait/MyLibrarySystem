package com.library.ai.contract;

import java.time.Instant;
import java.util.Map;

public record AiEvent(long eventId, String type, Map<String, Object> payload, Instant occurredAt) {
    public AiEvent { payload = Map.copyOf(payload); }
}
