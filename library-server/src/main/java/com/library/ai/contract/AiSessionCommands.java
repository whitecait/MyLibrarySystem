package com.library.ai.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;

public interface AiSessionCommands {
    ModuleResult<AiSession> start(AiSessionOwner owner, ActorContext actor);
    ModuleResult<AiTurn> turn(AiTurnCommand command, AiSessionOwner owner);
}
