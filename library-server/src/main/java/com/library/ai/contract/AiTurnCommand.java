package com.library.ai.contract;

import com.library.contract.security.ActorContext;

public record AiTurnCommand(long sessionId, String message, ActorContext actor) {}
