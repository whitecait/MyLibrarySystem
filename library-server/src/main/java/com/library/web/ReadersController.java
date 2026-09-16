package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.security.ActorContext;
import com.library.identity.contract.AccountChange;
import com.library.application.registration.RegistrationUseCases;
import com.library.web.Envelope;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpenAPI 操作 changeReaderStatus（PATCH /readers/{readerId}/status）。
 */
@RestController
@RequestMapping("/api/v1")
public class ReadersController {

    private final RegistrationUseCases registration;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final ApiResponses responses;

    public ReadersController(RegistrationUseCases registration,
                             com.library.contract.idempotency.IdempotencyService idempotency,
                             ApiResponses responses) {
        this.registration = registration;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record ReaderStatusWebRequest(String status, String reason) {
    }

    @PatchMapping("/readers/{readerId}/status")
    public ResponseEntity<Envelope> changeReaderStatus(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long readerId,
            @RequestBody ReaderStatusWebRequest body) {
        var actor = Actors.actor(request);
        if (actor == null) {
            return responses.forResult(
                    ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：需要登录"),
                    StableErrorCode.MLS_ID_001.code(), null, path(readerId), null, null);
        }
        var outcome = idempotency.execute(
                new IdempotencyScope(actor.accountId().asString(), "PATCH", path(readerId)),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        (body.status() == null ? "" : body.status()) + "" + (body.reason() == null ? "" : body.reason())),
                SuccessCodes.of("changeReaderStatus"),
                AccountChange.class,
                () -> registration.changeReaderStatus(actor, readerId, body.status(), body.reason()));
        return responses.forResult(outcome.result(), outcome.code(),
                change -> Map.of(
                        "readerId", change.accountId().asString(),
                        "status", change.status(),
                        "version", change.version()));
    }

    private static String path(long readerId) {
        return "/api/v1/readers/" + readerId + "/status";
    }
}
