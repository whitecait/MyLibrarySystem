package com.library.application.assistant;

import com.library.ai.contract.*;
import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** HTTP Adapter 使用的 AI 公共应用入口。 */
@Component
public class AssistantUseCases {
    private final AiSessionCommands commands;
    private final AiEventQuery events;
    private final AiOwnerResolver owners;
    public AssistantUseCases(AiSessionCommands commands,AiEventQuery events,AiOwnerResolver owners){this.commands=commands;this.events=events;this.owners=owners;}
    public ModuleResult<AiSession> start(AiSessionOwner owner, ActorContext actor){return commands.start(owner,actor);}
    public ModuleResult<AiTurn> turn(long sessionId,String message,AiSessionOwner owner,ActorContext actor){return commands.turn(new AiTurnCommand(sessionId,message,actor),owner);}
    public ModuleResult<AiEventPage> events(AiEventCriteria criteria,AiSessionOwner owner,ActorContext admin){return events.events(criteria,owner,Optional.ofNullable(admin));}
    /** 已存所有者类型选择凭据；同时携带另一凭据不会迁移或提权。 */
    public AiSessionOwner ownerFor(long sessionId, ActorContext actor, VisitorOwnerDigest visitor) {
        return owners.ownerFor(sessionId,actor==null?null:new AccountAiOwner(actor.accountId()),
                visitor==null?null:new VisitorAiOwner(visitor));
    }
}
