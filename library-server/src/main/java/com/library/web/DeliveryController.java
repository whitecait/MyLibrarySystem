package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.*;
import com.library.contract.model.PageCursor;
import com.library.delivery.contract.*;
import com.library.web.support.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class DeliveryController {
    private final NotificationQuery notifications;private final NotificationCommands commands;private final AuditQuery audits;private final IdempotencyService idempotency;private final ApiResponses responses;
    public DeliveryController(NotificationQuery n,NotificationCommands c,AuditQuery a,IdempotencyService i,ApiResponses r){notifications=n;commands=c;audits=a;idempotency=i;responses=r;}
    @GetMapping("/notifications") public ResponseEntity<Envelope> notifications(HttpServletRequest req,@RequestParam(defaultValue="false")boolean unreadOnly,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/notifications");return responses.forResult(notifications.notifications(new NotificationQuery.NotificationCriteria(unreadOnly,PageCursor.of(cursor,limit)),actor),SuccessCodes.of("listNotifications"),v->{var m=new LinkedHashMap<String,Object>();m.put("items",v.items().stream().map(DeliveryController::note).toList());m.put("nextCursor",v.nextCursor());return m;});}
    @PostMapping("/notifications/{id}/read") public ResponseEntity<Envelope> read(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@PathVariable long id){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/notifications/"+id+"/read");var path="/api/v1/notifications/"+id+"/read";var out=idempotency.execute(new IdempotencyScope(actor.accountId().asString(),"POST",path),key,JdbcIdempotencyService.sha256(Long.toString(id)),SuccessCodes.of("markNotificationRead"),NotificationCommands.ReadReceipt.class,()->commands.markRead(id,actor));return responses.forResult(out.result(),out.code(),v->Map.of("read",v.read()));}
    @GetMapping("/audit-events") public ResponseEntity<Envelope> audit(HttpServletRequest req,@RequestParam(required=false)Long relatedId,@RequestParam(required=false)Long actorId,@RequestParam(required=false)String eventType,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/audit-events");return responses.forResult(audits.audit(new AuditQuery.AuditCriteria(relatedId,actorId,eventType,PageCursor.of(cursor,limit)),actor),SuccessCodes.of("listAuditEvents"),v->{var m=new LinkedHashMap<String,Object>();m.put("items",v.items().stream().map(DeliveryController::auditData).toList());m.put("nextCursor",v.nextCursor());return m;});}
    private static Object note(NotificationQuery.Notification n){var m=new LinkedHashMap<String,Object>();m.put("notificationId",Long.toString(n.notificationId()));m.put("type",n.type());m.put("message",n.message());m.put("read",n.read());m.put("relatedTxId",n.relatedTxId()==null?null:Long.toString(n.relatedTxId()));m.put("reservationDate",n.reservationDate()==null?null:n.reservationDate().toString());m.put("createdAt",n.createdAt().toString());return m;}
    private static Object auditData(AuditQuery.AuditEvent a){var m=new LinkedHashMap<String,Object>();m.put("eventId",Long.toString(a.eventId()));m.put("eventType",a.eventType());m.put("actorId",a.actorId()==null?"0":Long.toString(a.actorId()));m.put("relatedId",a.relatedId()==null?null:Long.toString(a.relatedId()));m.put("outcome",a.outcome());m.put("occurredAt",a.occurredAt().toString());return m;}
    private ResponseEntity<Envelope> unauthorized(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(),"未认证：需要登录"),StableErrorCode.MLS_ID_001.code(),null,path,null,null);}
}
