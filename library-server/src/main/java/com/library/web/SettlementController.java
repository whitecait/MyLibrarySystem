package com.library.web;

import com.library.application.payment.PaymentSimulatorPort;
import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.*;
import com.library.contract.model.PageCursor;
import com.library.review.contract.*;
import com.library.settlement.contract.*;
import com.library.settlement.contract.model.FineId;
import com.library.web.support.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class SettlementController {
    private final FineQuery fines; private final OrderCommands orders; private final OrderQuery orderQuery;
    private final PaymentSimulatorPort simulator; private final IdempotencyService idempotency; private final ApiResponses responses;
    public SettlementController(FineQuery f,OrderCommands o,OrderQuery q,PaymentSimulatorPort s,IdempotencyService i,ApiResponses a){fines=f;orders=o;orderQuery=q;simulator=s;idempotency=i;responses=a;}
    public record SimulationRequest(String orderId,String result,Long delayMs,Integer duplicateCount){}
    public record CallbackRequest(String eventId,String orderId,String result,String occurredAt){}

    @GetMapping("/fines")
    public ResponseEntity<Envelope> listFines(HttpServletRequest req,
            @RequestParam(required = false) Long readerId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        var actor = Actors.actor(req);
        if (actor == null) return unauthorized("/api/v1/fines");

        Long effectiveReaderId = readerId;
        if (actor.role() == com.library.contract.model.Role.READER) {
            effectiveReaderId = actor.accountId().value();
        }
        var page = fines.fines(new FineQuery.FineCriteria(effectiveReaderId, status, PageCursor.of(cursor, limit)));
        return responses.forResult(page, SuccessCodes.of("listFines"), v -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("items", v.items().stream().map(SettlementController::fineData).toList());
            m.put("nextCursor", v.nextCursor());
            return m;
        });
    }
    @PostMapping("/fines/{fineId}/orders") public ResponseEntity<Envelope> createOrder(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@PathVariable long fineId){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/fines/"+fineId+"/orders");var path="/api/v1/fines/"+fineId+"/orders";var out=idempotency.execute(scope(actor,path),key,hash(fineId),SuccessCodes.of("createOrder"),OrderCommands.Order.class,()->orders.createOrder(new OrderCommands.CreateOrder(FineId.of(fineId),actor.accountId().value())));return responses.forResult(out.result(),out.code(),SettlementController::orderData);}
    @GetMapping("/orders/occupied") public ResponseEntity<Envelope> listOccupiedOrders(HttpServletRequest req,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/orders/occupied");return responses.forResult(orderQuery.occupiedOrders(PageCursor.of(cursor,limit),actor),SuccessCodes.of("listOccupiedOrders"),v->{var m=new LinkedHashMap<String,Object>();m.put("items",v.items().stream().map(SettlementController::orderData).toList());m.put("nextCursor",v.nextCursor());return m;});}
    @GetMapping("/orders/{orderId}") public ResponseEntity<Envelope> getOrder(HttpServletRequest req,@PathVariable long orderId){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/orders/"+orderId);return responses.forResult(orderQuery.order(orderId,actor),SuccessCodes.of("getOrder"),SettlementController::orderData);}
    @PostMapping("/payment-simulator/results") public ResponseEntity<Envelope> simulate(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@RequestBody SimulationRequest body){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/payment-simulator/results");if(actor.role()!=com.library.contract.model.Role.ADMIN)return forbidden("/api/v1/payment-simulator/results");var path="/api/v1/payment-simulator/results";var out=idempotency.execute(scope(actor,path),key,hash(body),SuccessCodes.of("scheduleSimulatedPayment"),PaymentSimulatorPort.Simulation.class,()->simulator.schedule(new PaymentSimulatorPort.Command(Long.parseLong(body.orderId()),body.result(),body.delayMs()==null?0:body.delayMs(),body.duplicateCount()==null?0:body.duplicateCount(),actor.accountId().value())));return responses.forResult(out.result(),out.code(),v->Map.of("simulationId",v.simulationId(),"status",v.status()));}
    @PostMapping("/payment-callbacks") public ResponseEntity<Envelope> callback(@RequestHeader("Idempotency-Key")String key,@RequestBody CallbackRequest body){var path="/api/v1/payment-callbacks";var out=idempotency.execute(new IdempotencyScope("payment-simulator","POST",path),key,hash(body),SuccessCodes.of("applyPaymentCallback"),OrderCommands.Callback.class,()->orders.applyCallback(new OrderCommands.PaymentCallback(body.eventId(),Long.parseLong(body.orderId()),body.result(),Instant.parse(body.occurredAt()))));return responses.forResult(out.result(),out.code(),v->Map.of("eventId",v.eventId(),"orderId",Long.toString(v.orderId()),"outcome",v.outcome()));}
    @PostMapping("/orders/{orderId}/close")
    public ResponseEntity<Envelope> close(HttpServletRequest req,
            @RequestHeader("Idempotency-Key") String key, @PathVariable long orderId) {
        var actor = Actors.actor(req);
        if (actor == null) return unauthorized("/api/v1/orders/" + orderId + "/close");

        // Idempotency may replay a cached response without invoking the callback.
        // Recheck live order visibility before entering that boundary.
        var visible = orderQuery.order(orderId, actor);
        if (visible instanceof ModuleResult.Rejected<OrderCommands.Order> rejected) {
            return responses.forResult(ModuleResult.Rejected.propagate(rejected),
                    SuccessCodes.of("closeOrder"), SettlementController::orderData);
        }

        var path = "/api/v1/orders/" + orderId + "/close";
        var out = idempotency.execute(scope(actor, path), key, hash(orderId),
                SuccessCodes.of("closeOrder"), OrderCommands.Order.class,
                () -> orders.closeOrder(new OrderCommands.CloseOrder(orderId, actor)));
        return responses.forResult(out.result(), out.code(), SettlementController::orderData);
    }
    @PostMapping("/orders/{orderId}/refunds") public ResponseEntity<Envelope> refund(@PathVariable String orderId){return unsupported("/api/v1/orders/"+orderId+"/refunds","退款功能已下线");}
    @PostMapping("/reconciliations") public ResponseEntity<Envelope> reconcile(){return unsupported("/api/v1/reconciliations","对账功能已下线");}
    private ResponseEntity<Envelope> unauthorized(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(),"未认证：需要登录"),StableErrorCode.MLS_ID_001.code(),null,path,null,null);}
    /** 控制器层角色检查的拒绝响应（MLS-ID-005；循 ReviewController.java:25 / CirculationController.java:436-437 先例）。 */
    private ResponseEntity<Envelope> forbidden(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(),"无权调度模拟支付"),StableErrorCode.MLS_ID_005.code(),null,path,null,null);}
    private ResponseEntity<Envelope> unsupported(String path,String message){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_009.code(),message),StableErrorCode.MLS_PAY_009.code(),null,path,null,null);}
    private static Object fineData(FineQuery.FineView f){return Map.of("fineId",Long.toString(f.fineId()),"readerId",Long.toString(f.readerId()),"amountCents",f.amountCents(),"status",f.status(),"createdAt",f.createdAt().toString());}
    private static Object orderData(OrderCommands.Order o){var m=new LinkedHashMap<String,Object>();m.put("orderId",Long.toString(o.orderId()));m.put("fineId",o.fineId().asString());m.put("amountCents",o.amountCents());m.put("status",o.status());m.put("expiresAt",o.expiresAt()==null?null:o.expiresAt().toString());return m;}
    private static IdempotencyScope scope(com.library.contract.security.ActorContext a,String path){return new IdempotencyScope(a.accountId().asString(),"POST",path);}private static String hash(Object o){return JdbcIdempotencyService.sha256(String.valueOf(o));}
}
