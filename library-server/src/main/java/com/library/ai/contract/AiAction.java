package com.library.ai.contract;

import java.util.Map;

public record AiAction(String actionType, String level, Map<String, Object> prefill,
                       long draftId, String label) {
    public AiAction { prefill = Map.copyOf(prefill); }
}
