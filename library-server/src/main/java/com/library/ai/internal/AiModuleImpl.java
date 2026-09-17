package com.library.ai.internal;

import com.library.ai.contract.*;
import com.library.ai.internal.adapter.persistence.AiSessionStore;
import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.contract.time.BusinessClock;
import com.library.identity.contract.AuthorizationCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/** AI 深模块：所有权、额度、模型循环、工具授权与事件事实由一个公共接口封装。 */
@Component
public class AiModuleImpl implements AiModule {
    private final AiSessionStore sessions;
    private final ModelEndpoint model;
    private final AuthorizationCheck authorization;
    private final BusinessClock clock;
    private final ToolRegistry tools;
    private final ToolExecutor toolExecutor;
    private final int accountDailyLimit;
    private final int visitorDailyLimit;
    private final int maxRounds;
    private final int retryBudget;
    private final Duration timeout;

    public AiModuleImpl(AiSessionStore sessions, ModelEndpoint model, AuthorizationCheck authorization,
                        BusinessClock clock, ToolExecutor toolExecutor,
                        @Value("${library.ai.account-daily-limit:10}") int accountDailyLimit,
                        @Value("${library.ai.visitor-daily-limit:10}") int visitorDailyLimit,
                        @Value("${library.ai.max-rounds:4}") int maxRounds,
                        @Value("${library.ai.retry-budget:2}") int retryBudget,
                        @Value("${library.ai.endpoint-timeout:8s}") Duration timeout) {
        this.sessions=sessions; this.model=model; this.authorization=authorization; this.clock=clock;
        this.tools=ToolRegistry.approved(); this.toolExecutor=toolExecutor; this.accountDailyLimit=accountDailyLimit;
        this.visitorDailyLimit=visitorDailyLimit; this.maxRounds=maxRounds;
        this.retryBudget=retryBudget; this.timeout=timeout;
    }

    @Override public ModuleResult<AiSession> start(AiSessionOwner owner, ActorContext actor) {
        if (owner instanceof AccountAiOwner account && (actor == null || actor.accountId().value() != account.accountId().value()))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_005.code(), "会话不可见");
        var role = owner instanceof VisitorAiOwner ? "VISITOR" : actor.role().name();
        var stored = sessions.create(owner, role, clock.now());
        sessions.appendEvent(stored.id(), "SESSION_STARTED", Map.of("role", role), clock.now());
        return ModuleResult.Accepted.of(new AiSession(stored.id(), role,
                new AiSession.Quota(sessions.remaining(owner, clock.today(), limit(owner)))));
    }
    @Override public AiSessionOwner ownerFor(long sessionId, AccountAiOwner account, VisitorAiOwner visitor) {
        var stored=sessions.session(sessionId).orElse(null);
        if(stored!=null&&stored.visitorDigest()!=null&&visitor!=null)return visitor;
        if(stored!=null&&stored.accountId()!=null&&account!=null)return account;
        return visitor!=null?visitor:account;
    }

    @Override public ModuleResult<AiTurn> turn(AiTurnCommand command, AiSessionOwner owner) {
        if (command == null || command.message() == null || command.message().isBlank() || command.message().length()>4000)
            return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_004.code(), "消息必须为 1~4000 字符");
        var stored = sessions.session(command.sessionId()).orElse(null);
        if (stored == null || !sessions.ownedBy(stored, owner))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_005.code(), "会话不可见");
        if (owner instanceof AccountAiOwner account && (command.actor()==null || command.actor().accountId().value()!=account.accountId().value()))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_005.code(), "会话不可见");

        long userMessage = sessions.appendMessage(command.sessionId(), "USER", command.message(), Map.of(), clock.now());
        sessions.appendEvent(command.sessionId(), "USER_MESSAGE", Map.of("messageId", userMessage), clock.now());
        var conversation = new ArrayList<Map<String,Object>>();
        conversation.add(Map.of("role","user","content",command.message()));
        var actions = new ArrayList<AiAction>();
        var citations = new ArrayList<String>();
        String answer = "";
        int successfulRounds=0;
        while (successfulRounds < maxRounds) {
            var response = invokeWithRetries(command.sessionId(), owner, command.actor(), conversation);
            if (response instanceof ModuleResult.Rejected<ModelEndpoint.ModelResponse> rejected)
                return ModuleResult.Rejected.propagate(rejected);
            var value = ((ModuleResult.Accepted<ModelEndpoint.ModelResponse>) response).value();
            successfulRounds++;
            answer = value.answer() == null ? "" : value.answer();
            citations.addAll(value.citations());
            if (value.toolCalls().isEmpty()) break;
            for (var call : value.toolCalls()) {
                var result = executeTool(command.sessionId(), owner, command.actor(), call, actions);
                if (result instanceof ModuleResult.Rejected<String> rejected)
                    return ModuleResult.Rejected.propagate(rejected);
                conversation.add(Map.of("role","tool","name",call.name(),"content",((ModuleResult.Accepted<String>)result).value()));
            }
        }
        if (successfulRounds == maxRounds) {
            sessions.status(command.sessionId(), "MAX_TURNS", clock.now());
            sessions.appendEvent(command.sessionId(), "TERMINATED", Map.of("reason","MAX_TURNS"), clock.now());
        }
        long assistantMessage = sessions.appendMessage(command.sessionId(), "ASSISTANT", answer, Map.of(), clock.now());
        sessions.appendEvent(command.sessionId(), "ASSISTANT_MESSAGE", Map.of("messageId",assistantMessage), clock.now());
        return ModuleResult.Accepted.of(new AiTurn(assistantMessage, answer, citations, actions,
                new AiSession.Quota(sessions.remaining(owner, clock.today(), limit(owner)))));
    }

    private ModuleResult<ModelEndpoint.ModelResponse> invokeWithRetries(long sessionId, AiSessionOwner owner,
            ActorContext actor, List<Map<String,Object>> messages) {
        for (int attempt=0; attempt<=retryBudget; attempt++) {
            if (!sessions.tryConsume(owner, clock.today(), limit(owner))) {
                sessions.status(sessionId,"QUOTA_BLOCKED",clock.now());
                sessions.appendEvent(sessionId,"QUOTA_BLOCKED",Map.of("remaining",0),clock.now());
                return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_002.code(), "quota exhausted");
            }
            sessions.appendEvent(sessionId,"MODEL_REQUEST",Map.of("attempt",attempt+1,"thinking","disabled"),clock.now());
            try {
                var response=model.complete(new ModelEndpoint.ModelRequest(messages, tools.availableTo(owner,actor),
                        Map.of("type","disabled"),timeout));
                sessions.appendEvent(sessionId,"MODEL_RESPONSE",Map.of("attempt",attempt+1,"toolCalls",response.toolCalls().size()),clock.now());
                return ModuleResult.Accepted.of(response);
            } catch (ModelEndpoint.ModelUnavailable unavailable) {
                sessions.refund(owner,clock.today());
                sessions.appendEvent(sessionId,"MODEL_UNAVAILABLE",Map.of("reason",safe(unavailable.getMessage())),clock.now());
                return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_001.code(),"AI 不可用");
            } catch (ModelEndpoint.ModelFailure failure) {
                sessions.refund(owner,clock.today());
                sessions.appendEvent(sessionId,attempt<retryBudget?"MODEL_RETRY":"MODEL_FAILURE",
                        Map.of("attempt",attempt+1,"reason",safe(failure.getMessage())),clock.now());
            }
        }
        sessions.status(sessionId,"ERROR",clock.now());
        return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_003.code(), "模型端点失败");
    }

    private ModuleResult<String> executeTool(long sessionId, AiSessionOwner owner, ActorContext actor,
            ModelEndpoint.ToolCall call, List<AiAction> actions) {
        final AiTool tool;
        try { tool=tools.require(call.name()); }
        catch (ToolRegistry.ToolRejected rejected) { return rejectTool(sessionId,"未登记工具"); }
        if (!tools.availableTo(owner,actor).contains(tool)) return rejectTool(sessionId,"工具越权");
        if (call.arguments().keySet().stream().anyMatch(Set.of("role","accountId","readerId","amountCents","version")::contains))
            return rejectTool(sessionId,"模型参数包含服务端受控字段");
        if (!"SESSION_OWNER_OR_ADMIN".equals(tool.requiredCapability())) {
            var auth=authorization.authorize(actor,new Capability(tool.requiredCapability()),
                    actor == null ? SubjectRef.system() : actor.subjectRef());
            if (auth instanceof ModuleResult.Rejected<?>) return rejectTool(sessionId,"工具授权失败");
        }
        sessions.appendEvent(sessionId,"TOOL_CALL",Map.of("name",tool.name(),"level",tool.level()),clock.now());
        if ("L2".equals(tool.level()) || "L3".equals(tool.level())) {
            long eventId=sessions.appendEvent(sessionId,"ACTION_DRAFT",Map.of("actionType",tool.name(),"level",tool.level(),"prefill",call.arguments()),clock.now());
            actions.add(new AiAction(actionType(tool.name()),tool.level(),call.arguments(),eventId,
                    "L2".equals(tool.level())?"打开并核对表单":"打开办理页面"));
        }
        String result="{\"status\":\"ACTION_ONLY\"}";
        if("L1".equals(tool.level())&&!"list_ai_events".equals(tool.name())) {
            try { result=toolExecutor.execute(tool.name(),call.arguments(),actor); }
            catch(RuntimeException failure){ return rejectTool(sessionId,"工具参数或查询失败"); }
        }
        sessions.appendEvent(sessionId,"TOOL_RESULT",Map.of("name",tool.name(),"result","READ_ONLY"),clock.now());
        return ModuleResult.Accepted.of(result);
    }
    private ModuleResult<String> rejectTool(long sessionId,String reason) {
        sessions.appendEvent(sessionId,"TOOL_REJECTED",Map.of("reason",reason),clock.now());
        return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_004.code(),reason);
    }
    @Override public ModuleResult<AiEventPage> events(AiEventCriteria query, AiSessionOwner owner, Optional<ActorContext> admin) {
        var stored=sessions.session(query.sessionId()).orElse(null);
        boolean adminAllowed=admin.filter(a->a.role()==Role.ADMIN).isPresent();
        if (stored==null || (!sessions.ownedBy(stored,owner) && !adminAllowed))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_AI_005.code(),"会话不可见");
        var page=query.page()==null? PageCursor.first():query.page();
        long cursor=page.cursor()==null?0:Long.parseLong(page.cursor());
        var rows=sessions.events(query.sessionId(),cursor,page.limit()+1);
        boolean more=rows.size()>page.limit();
        var items=rows.stream().limit(page.limit()).toList();
        return ModuleResult.Accepted.of(new AiEventPage(items,more?String.valueOf(items.getLast().eventId()):null));
    }
    private int limit(AiSessionOwner owner){ return owner instanceof VisitorAiOwner?visitorDailyLimit:accountDailyLimit; }
    private String safe(String s){ return s==null?"failure":s.substring(0,Math.min(120,s.length())); }
    private String actionType(String tool) {
        return switch(tool) {
            case "prefill_reservation" -> "CREATE_RESERVATION";
            case "prefill_renewal" -> "RENEW_LOAN";
            case "prefill_walk_in_loan" -> "WALK_IN_LOAN";
            case "prefill_return" -> "RETURN_COPY";
            default -> tool.toUpperCase(Locale.ROOT);
        };
    }
}
