package com.library.ai.contract;

import java.util.List;

public record AiTurn(long messageId, String answer, List<String> citations,
                     List<AiAction> actions, AiSession.Quota quota) {
    public AiTurn { citations = List.copyOf(citations); actions = List.copyOf(actions); }
}
