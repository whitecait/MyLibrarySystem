package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.AvailabilityQuery;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.ReturnCommands;
import com.library.circulation.contract.LoanCommands;
import com.library.circulation.contract.ReservationCommands;
import com.library.circulation.contract.Transaction;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.CopyId;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;
import com.library.application.loan.LoanUseCases;
import com.library.application.maintenance.MaintenanceUseCases;
import com.library.application.returning.ReturnUseCases;
import com.library.web.Envelope;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI Circulation 操作（B2：11 个；runClosing/reopenBusinessDate 随 B4）。
 * 写操作经幂等服务（作用域=认证主体+方法+规范路径+键）。
 */
@RestController
@RequestMapping("/api/v1")
public class CirculationController {

    private final MaintenanceUseCases maintenance;
    private final LoanUseCases loans;
    private final ReturnUseCases returns;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final ApiResponses responses;

    public CirculationController(MaintenanceUseCases maintenance,
                                 LoanUseCases loans,
                                 ReturnUseCases returns,
                                 com.library.contract.idempotency.IdempotencyService idempotency,
                                 ApiResponses responses) {
        this.maintenance = maintenance;
        this.loans = loans;
        this.returns = returns;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record CreateCopyWebRequest(String copyNo) {
    }

    public record CopyActionWebRequest(String action, long version) {
    }

    public record ReservationWebRequest(String titleId, String reservationDate, String pickupSlot,
                                        String channel, String onBehalfOf, Boolean confirmWaitlist) {
    }

    public record ReasonWebRequest(String reason) {
    }

    public record WalkInItemWebRequest(String titleId, String copyId) {
    }

    public record WalkInLoanWebRequest(String readerId, List<WalkInItemWebRequest> items,
                                       String channel) {
    }

    public record ClaimWebRequest(String copyId) {
    }

    public record ManualActionWebRequest(String action, String reason) {
    }

    public record RenewalWebRequest(String requestedDueDate) {
    }

    public record ReturnWebRequest(String copyId, String condition) {
    }

    // ---- 副本维护 -----------------------------------------------------------

    @PostMapping("/titles/{titleId}/copies")
    public ResponseEntity<Envelope> createCopy(HttpServletRequest request,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @PathVariable long titleId,
                                               @RequestBody CreateCopyWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, path(titleId) + "/copies");
        }
        var outcome = idempotency.execute(scope(actor, "POST", path(titleId) + "/copies"),
                key, hash(body.copyNo()), SuccessCodes.of("createCopy"),
                CopyCommands.CopySnapshot.class,
                () -> maintenance.createCopy(actor, new CopyCommands.CreateCopy(
                        TitleId.of(titleId), body.copyNo(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), path(titleId) + "/copies",
                CirculationController::toCopyData);
    }

    @GetMapping("/copies")
    public ResponseEntity<Envelope> listCopies(HttpServletRequest request,
                                               @RequestParam(required = false) String titleId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, "/api/v1/copies");
        }
        var criteria = new CopyCommands.CopyCriteria(
                titleId == null ? null : TitleId.of(Long.parseLong(titleId)), status,
                PageCursor.of(cursor, limit));
        var result = maintenance.listCopies(actor, criteria);
        return responses.forResult(result, SuccessCodes.of("listCopies"),
                Actors.actorId(request), "/api/v1/copies",
                page -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("items", page.items().stream().map(CirculationController::toCopyData).toList());
                    data.put("nextCursor", page.nextCursor());
                    return data;
                });
    }

    @PatchMapping("/copies/{copyId}")
    public ResponseEntity<Envelope> changeCopyState(HttpServletRequest request,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @PathVariable long copyId,
                                                    @RequestBody CopyActionWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, "/api/v1/copies/" + copyId);
        }
        var outcome = idempotency.execute(scope(actor, "PATCH", "/api/v1/copies/" + copyId),
                key, hash(body.action() + "" + body.version()), SuccessCodes.of("changeCopyState"),
                CopyCommands.CopySnapshot.class,
                () -> maintenance.changeCopyState(actor, new CopyCommands.MaintainCopy(
                        CopyId.of(copyId), body.action(), body.version(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), "/api/v1/copies/" + copyId,
                CirculationController::toCopyData);
    }

    // ---- 预约日历与预约 ----------------------------------------------------------

    @GetMapping("/titles/{titleId}/reservation-calendar")
    public ResponseEntity<Envelope> reservationCalendar(@PathVariable long titleId,
                                                        @RequestParam String from,
                                                        @RequestParam String to) {
        BusinessDate fromDate;
        BusinessDate toDate;
        try {
            fromDate = BusinessDate.parse(from);
            toDate = BusinessDate.parse(to);
        } catch (Exception e) {
            return fieldError(path(titleId) + "/reservation-calendar", "日期格式无效（YYYY-MM-DD）");
        }
        var result = maintenance.reservationCalendar(TitleId.of(titleId), fromDate, toDate);
        return responses.forResult(result, SuccessCodes.of("getReservationCalendar"),
                calendar -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("items", calendar.days().stream().map(day -> Map.of(
                            "businessDate", day.businessDate().asString(),
                            "availability", day.availability())).toList());
                    return data;
                });
    }

    @PostMapping("/reservations")
    public ResponseEntity<Envelope> reserve(HttpServletRequest request,
                                            @RequestHeader("Idempotency-Key") String key,
                                            @RequestBody ReservationWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, "/api/v1/reservations");
        }
        ReservationCommands.ReservationCommand command;
        try {
            command = new ReservationCommands.ReservationCommand(
                    onBehalfOfId(body.onBehalfOf()), TitleId.of(Long.parseLong(body.titleId())),
                    BusinessDate.parse(body.reservationDate()), body.pickupSlot(), body.channel(),
                    Boolean.TRUE.equals(body.confirmWaitlist()), actor);
        } catch (Exception e) {
            return fieldError("/api/v1/reservations", "预约请求字段无效: " + e);
        }
        var outcome = idempotency.execute(scope(actor, "POST", "/api/v1/reservations"),
                key, hash(body), SuccessCodes.of("reserveTitle"),
                ReservationCommands.Reservation.class,
                () -> loans.reserve(actor, command));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), "/api/v1/reservations",
                reservation -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("txId", reservation.txId() == null ? null : reservation.txId().asString());
                    data.put("outcome", reservation.outcome());
                    data.put("reasons", reservation.reasons());
                    return data;
                });
    }

    @PostMapping("/reservations/{txId}/cancel")
    public ResponseEntity<Envelope> cancelReservation(HttpServletRequest request,
                                                      @RequestHeader("Idempotency-Key") String key,
                                                      @PathVariable long txId,
                                                      @RequestBody ReasonWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, cancelPath(txId));
        }
        var outcome = idempotency.execute(scope(actor, "POST", cancelPath(txId)),
                key, hash(body.reason()), SuccessCodes.of("cancelReservation"),
                Transaction.class,
                () -> loans.cancel(actor, new ReservationCommands.CancelReservation(
                        TxId.of(txId), body.reason(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), cancelPath(txId),
                CirculationController::toTransactionData);
    }

    // ---- 现场借阅 / 取书 / 人工处置 / 续借 ----------------------------------------

    @PostMapping("/walk-in-loans")
    public ResponseEntity<Envelope> walkInLoans(HttpServletRequest request,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @RequestBody WalkInLoanWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, "/api/v1/walk-in-loans");
        }
        List<LoanCommands.WalkInItem> items;
        try {
            items = body.items().stream()
                    .map(item -> new LoanCommands.WalkInItem(
                            TitleId.of(Long.parseLong(item.titleId())),
                            item.copyId() == null ? null : CopyId.of(Long.parseLong(item.copyId()))))
                    .toList();
        } catch (Exception e) {
            return fieldError("/api/v1/walk-in-loans", "逐册条目字段无效: " + e);
        }
        var outcome = idempotency.execute(scope(actor, "POST", "/api/v1/walk-in-loans"),
                key, hash(body), SuccessCodes.of("createWalkInLoans"),
                LoanCommands.LoanBatch.class,
                () -> loans.lend(actor, new LoanCommands.WalkInLoanCommand(
                        readerIdOrZero(body.readerId()), items, body.channel(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), "/api/v1/walk-in-loans",
                batch -> Map.of("items", batch.items().stream().map(item -> {
                    var itemData = new LinkedHashMap<String, Object>();
                    itemData.put("itemRef", item.itemRef());
                    itemData.put("txId", item.txId() == null ? null : item.txId().asString());
                    itemData.put("outcome", item.outcome());
                    itemData.put("reasons", item.reasons());
                    return itemData;
                }).toList()));
    }

    @PostMapping("/pickups/{txId}/claim")
    public ResponseEntity<Envelope> claimPickup(HttpServletRequest request,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @PathVariable long txId,
                                                @RequestBody ClaimWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, claimPath(txId));
        }
        var outcome = idempotency.execute(scope(actor, "POST", claimPath(txId)),
                key, hash(body.copyId()), SuccessCodes.of("claimPickup"),
                Transaction.class,
                () -> loans.claim(actor, new LoanCommands.ClaimPickup(
                        TxId.of(txId), CopyId.of(Long.parseLong(body.copyId())), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), claimPath(txId),
                CirculationController::toTransactionData);
    }

    @PostMapping("/transactions/{txId}/manual-actions")
    public ResponseEntity<Envelope> applyManualAction(HttpServletRequest request,
                                                      @RequestHeader("Idempotency-Key") String key,
                                                      @PathVariable long txId,
                                                      @RequestBody ManualActionWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, manualPath(txId));
        }
        var outcome = idempotency.execute(scope(actor, "POST", manualPath(txId)),
                key, hash(body.action() + "" + body.reason()), SuccessCodes.of("applyManualAction"),
                Transaction.class,
                () -> loans.applyManualAction(actor, new LoanCommands.ManualAction(
                        TxId.of(txId), body.action(), body.reason(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), manualPath(txId),
                CirculationController::toTransactionData);
    }

    @PostMapping("/loans/{txId}/renewals")
    public ResponseEntity<Envelope> renewLoan(HttpServletRequest request,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @PathVariable long txId,
                                              @RequestBody RenewalWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, renewPath(txId));
        }
        try {
            var outcome = idempotency.execute(scope(actor, "POST", renewPath(txId)),
                    key, hash(body.requestedDueDate()), SuccessCodes.of("renewLoan"),
                    LoanCommands.Renewal.class,
                    () -> loans.renew(actor, new LoanCommands.RenewalCommand(
                            TxId.of(txId),
                            body.requestedDueDate() == null ? null : BusinessDate.parse(body.requestedDueDate()),
                            actor)));
            return responses.forResult(outcome.result(), outcome.code(),
                    Actors.actorId(request), renewPath(txId),
                    renewal -> Map.of(
                            "txId", renewal.txId().asString(),
                            "dueDate", renewal.dueDate().asString(),
                            "outcome", renewal.outcome()));
        } catch (Exception e) {
            return fieldError(renewPath(txId), "续借请求字段无效");
        }
    }

    // ---- 归还（流通迁移+罚款+Outbox 同事务）----------------------------------------

    @PostMapping("/returns")
    public ResponseEntity<Envelope> returnCopy(HttpServletRequest request,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @RequestBody ReturnWebRequest body) {
        var actor = requireActor(request);
        if (actor == null) {
            return unauthorized(request, "/api/v1/returns");
        }
        if (body.copyId() == null) {
            return fieldError("/api/v1/returns", "copyId 必填");
        }
        var outcome = idempotency.execute(scope(actor, "POST", "/api/v1/returns"),
                key, hash(body), SuccessCodes.of("returnCopy"),
                ReturnUseCases.ReturnResult.class,
                () -> returns.returnCopy(actor, new ReturnCommands.ReturnCommand(
                        CopyId.of(Long.parseLong(body.copyId())), body.condition(), actor)));
        return responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), "/api/v1/returns",
                result -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("txId", result.returned().txId().asString());
                    data.put("outcome", result.returned().outcome());
                    data.put("fineId", result.fineId() == null ? null : String.valueOf(result.fineId()));
                    return data;
                });
    }

    // ---- helpers ---------------------------------------------------------------

    private static ActorContext requireActor(HttpServletRequest request) {
        return Actors.actor(request);
    }

    private long onBehalfOfId(String onBehalfOf) {
        return onBehalfOf == null ? 0 : Long.parseLong(onBehalfOf);
    }

    private long readerIdOrZero(String readerId) {
        return readerId == null ? 0 : Long.parseLong(readerId);
    }

    private static Map<String, Object> toCopyData(CopyCommands.CopySnapshot snapshot) {
        var data = new LinkedHashMap<String, Object>();
        data.put("copyId", snapshot.copyId().asString());
        data.put("titleId", snapshot.titleId().asString());
        data.put("copyNo", snapshot.copyNo());
        data.put("status", snapshot.status());
        data.put("version", snapshot.version());
        return data;
    }

    private static Map<String, Object> toTransactionData(Transaction tx) {
        var data = new LinkedHashMap<String, Object>();
        data.put("txId", tx.txId().asString());
        data.put("outcome", tx.outcome());
        data.put("status", tx.status());
        return data;
    }

    private ResponseEntity<Envelope> unauthorized(HttpServletRequest request, String path) {
        return responses.forResult(
                ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：需要登录"),
                StableErrorCode.MLS_ID_001.code(), null, path, null, null);
    }

    private ResponseEntity<Envelope> fieldError(String path, String message) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Actors.declareFieldErrorCode(servlet.getRequest(), StableErrorCode.MLS_CIRC_001.code());
        }
        return ResponseEntity.badRequest().body(Envelope.of(
                StableErrorCode.MLS_CIRC_001.code(), message, Map.of("reasons", List.of(message))));
    }

    private IdempotencyScope scope(ActorContext actor, String method, String path) {
        return new IdempotencyScope(actor.accountId().asString(), method, path);
    }

    private String hash(Object canonical) {
        return com.library.contract.idempotency.JdbcIdempotencyService.sha256(String.valueOf(canonical));
    }

    private static String path(long titleId) {
        return "/api/v1/titles/" + titleId;
    }

    private static String cancelPath(long txId) {
        return "/api/v1/reservations/" + txId + "/cancel";
    }

    private static String claimPath(long txId) {
        return "/api/v1/pickups/" + txId + "/claim";
    }

    private static String manualPath(long txId) {
        return "/api/v1/transactions/" + txId + "/manual-actions";
    }

    private static String renewPath(long txId) {
        return "/api/v1/loans/" + txId + "/renewals";
    }
}
