package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.identity.contract.AccountChange;
import com.library.identity.contract.AccountPage;
import com.library.identity.contract.TemporaryPassword;
import com.library.application.registration.RegistrationUseCases;
import com.library.review.contract.Review;
import com.library.web.Envelope;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI 操作 listAccounts / requestAccountChange / resetPassword（Identity）。
 */
@RestController
@RequestMapping("/api/v1")
public class AccountsController {

    private final RegistrationUseCases registration;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final ApiResponses responses;

    public AccountsController(RegistrationUseCases registration,
                              com.library.contract.idempotency.IdempotencyService idempotency,
                              ApiResponses responses) {
        this.registration = registration;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record AccountChangeWebRequest(String action, Map<String, Object> payload, String reason) {
    }

    public record ReasonWebRequest(String reason) {
    }

    @GetMapping("/accounts")
    public ResponseEntity<Envelope> listAccounts(
            HttpServletRequest request,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var actor = requireActor(request);
        if (actor instanceof ModuleResult.Rejected<ActorContext> rejected) {
            return responses.forResult(ModuleResult.Rejected.propagate(rejected),
                    rejected.code(), Actors.actorId(request), "/api/v1/accounts", null, null);
        }
        var result = registration.listAccounts(((ModuleResult.Accepted<ActorContext>) actor).value(),
                role, status, PageCursor.of(cursor, limit));
        return responses.forResult(result, SuccessCodes.of("listAccounts"),
                Actors.actorId(request), "/api/v1/accounts",
                page -> {
                    var data = new java.util.LinkedHashMap<String, Object>();
                    data.put("items", page.items().stream().map(a -> Map.of(
                            "accountId", a.accountId().asString(),
                            "username", a.username(),
                            "displayName", a.displayName(),
                            "role", a.role(),
                            "status", a.status(),
                            "version", a.version())).toList());
                    data.put("nextCursor", page.nextCursor());
                    return data;
                });
    }

    @PostMapping("/accounts/{accountId}/change-requests")
    public ResponseEntity<Envelope> requestAccountChange(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long accountId,
            @RequestBody AccountChangeWebRequest body) {
        var actor = requireActor(request);
        if (actor instanceof ModuleResult.Rejected<ActorContext> rejected) {
            return responses.forResult(ModuleResult.Rejected.propagate(rejected),
                    rejected.code(), Actors.actorId(request), path(accountId), null, null);
        }
        var outcome = idempotency.execute(
                new IdempotencyScope(((ModuleResult.Accepted<ActorContext>) actor).value().accountId().asString(),
                        "POST", path(accountId)),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        body.action() + "" + body.payload() + "" + body.reason()),
                SuccessCodes.of("requestAccountChange"),
                Review.class,
                () -> registration.requestAccountChange(
                        ((ModuleResult.Accepted<ActorContext>) actor).value(),
                        accountId, body.action(), body.payload(), body.reason()));
        return responses.forResult(outcome.result(), outcome.code(),
                review -> Map.of("reviewId", String.valueOf(review.reviewId()), "status", review.status()));
    }

    @PostMapping("/accounts/{accountId}/password-reset")
    public ResponseEntity<Envelope> resetPassword(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long accountId,
            @RequestBody ReasonWebRequest body) {
        var actor = requireActor(request);
        if (actor instanceof ModuleResult.Rejected<ActorContext> rejected) {
            return responses.forResult(ModuleResult.Rejected.propagate(rejected),
                    rejected.code(), Actors.actorId(request), path(accountId), null, null);
        }
        var outcome = idempotency.execute(
                new IdempotencyScope(((ModuleResult.Accepted<ActorContext>) actor).value().accountId().asString(),
                        "POST", path(accountId)),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        "reset" + "" + body.reason()),
                SuccessCodes.of("resetPassword"),
                TemporaryPassword.class,
                () -> registration.resetPassword(
                        ((ModuleResult.Accepted<ActorContext>) actor).value(), accountId, body.reason()));
        return responses.forResult(outcome.result(), outcome.code(),
                password -> Map.of("temporaryPassword", password.value(), "forceChange", true));
    }

    private static String path(long accountId) {
        return "/api/v1/accounts/" + accountId;
    }

    private static ModuleResult<ActorContext> requireActor(HttpServletRequest request) {
        var actor = Actors.actor(request);
        if (actor == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：需要登录");
        }
        return ModuleResult.Accepted.of(actor);
    }
}
