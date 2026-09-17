package com.library.ai.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;

import java.util.Optional;

public interface AiEventQuery {
    ModuleResult<AiEventPage> events(AiEventCriteria query, AiSessionOwner owner, Optional<ActorContext> admin);
}
