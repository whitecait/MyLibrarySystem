package com.library.web;

import com.library.ai.contract.*;
import com.library.application.assistant.AssistantUseCases;
import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.contract.idempotency.IdempotencyService;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.idempotency.JdbcIdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/sessions")
public class AiController {
    private final AssistantUseCases assistant; private final AiVisitorCookieAdapter cookies; private final ApiResponses responses; private final IdempotencyService idempotency;
    public AiController(AssistantUseCases assistant,AiVisitorCookieAdapter cookies,ApiResponses responses,IdempotencyService idempotency){this.assistant=assistant;this.cookies=cookies;this.responses=responses;this.idempotency=idempotency;}

    @PostMapping
    public ResponseEntity<Envelope> start(@RequestHeader("Idempotency-Key") String idempotencyKey,HttpServletRequest request){
        var actor=Actors.actor(request);
        AiVisitorCookieAdapter.Issued issued=null;
        AiSessionOwner owner;
        if(actor==null){var existing=cookies.rawFrom(request.getCookies());issued=existing==null?cookies.recoverable(idempotencyKey):new AiVisitorCookieAdapter.Issued(existing,cookies.digest(existing));owner=new VisitorAiOwner(issued.digest());}
        else owner=new AccountAiOwner(actor.accountId());
        var outcome=idempotency.execute(new IdempotencyScope(owner.persistenceKey(),"POST","/api/v1/ai/sessions"),idempotencyKey,
                JdbcIdempotencyService.sha256("start:"+owner.persistenceKey()),"MLS-AI-S001",AiSession.class,()->assistant.start(owner,actor));
        var response=responses.forResult(outcome.result(),outcome.code(),this::sessionData);
        if(issued==null||!response.getStatusCode().is2xxSuccessful())return response;
        var cookie=ResponseCookie.from(AiVisitorCookieAdapter.NAME,issued.raw()).path("/").secure(true).httpOnly(true).sameSite("Strict").build();
        return ResponseEntity.status(response.getStatusCode()).header(HttpHeaders.SET_COOKIE,cookie.toString()).body(response.getBody());
    }
    public record MessageRequest(String message){}
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Envelope> message(@PathVariable long sessionId,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestBody MessageRequest body,HttpServletRequest request){
        var owner=owner(sessionId,request);
        if(owner==null)return unauthenticated();
        var path="/api/v1/ai/sessions/"+sessionId+"/messages";
        var outcome=idempotency.execute(new IdempotencyScope(owner.persistenceKey(),"POST",path),idempotencyKey,
                JdbcIdempotencyService.sha256(body.message()==null?"":body.message()),"MLS-AI-S001",AiTurn.class,
                ()->assistant.turn(sessionId,body.message(),owner,Actors.actor(request)));
        return responses.forResult(outcome.result(),outcome.code(),
                Actors.actorId(request), request.getRequestURI(),
                turn->Map.of("messageId",String.valueOf(turn.messageId()),"answer",turn.answer(),"citations",turn.citations(),
                        "actions",turn.actions().stream().map(this::actionData).toList(),"quota",Map.of("remaining",turn.quota().remaining())),
                rejected->"MLS-AI-002".equals(rejected.code())?Map.of("remaining",0):Map.of());
    }
    @GetMapping("/{sessionId}/events")
    public ResponseEntity<Envelope> events(@PathVariable long sessionId,@RequestParam(required=false)String cursor,
            @RequestParam(required=false)Integer limit,HttpServletRequest request){
        var owner=owner(sessionId,request);
        if(owner==null)return unauthenticated();
        return responses.forResult(assistant.events(new AiEventCriteria(sessionId,PageCursor.of(cursor,limit)),owner,Actors.actor(request)),"MLS-AI-S002",
                Actors.actorId(request), request.getRequestURI(),
                page->{var data=new LinkedHashMap<String,Object>();data.put("items",page.items().stream().map(e->Map.of("eventId",String.valueOf(e.eventId()),"type",e.type(),"payload",e.payload(),"occurredAt",e.occurredAt().toString())).toList());data.put("nextCursor",page.nextCursor());return data;},
                rejected->Map.of());
    }
    private AiSessionOwner owner(long id,HttpServletRequest request){return assistant.ownerFor(id,Actors.actor(request),cookies.from(request.getCookies()));}
    private ResponseEntity<Envelope> unauthenticated(){return ResponseEntity.status(401).body(Envelope.of(StableErrorCode.MLS_ID_001.code(),"未认证：缺少会话所有权凭据",Map.of()));}
    private Object sessionData(AiSession s){return Map.of("sessionId",String.valueOf(s.sessionId()),"role",s.role(),"quota",Map.of("remaining",s.quota().remaining()));}
    private Object actionData(AiAction a){return Map.of("actionType",a.actionType(),"level",a.level(),"prefill",a.prefill(),"draftId",String.valueOf(a.draftId()),"label",a.label());}
}
