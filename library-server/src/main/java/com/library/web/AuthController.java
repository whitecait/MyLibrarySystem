package com.library.web;

import com.library.contract.idempotency.IdempotencyScope;
import com.library.identity.contract.IssuedSession;
import com.library.identity.contract.SessionEnded;
import com.library.identity.contract.SessionToken;
import com.library.application.registration.RegistrationUseCases;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpenAPI 操作 createSession / endCurrentSession（Identity）。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    public static final String SESSIONS_PATH = "/api/v1/auth/sessions";

    private final RegistrationUseCases registration;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final ApiResponses responses;

    public AuthController(RegistrationUseCases registration,
                          com.library.contract.idempotency.IdempotencyService idempotency,
                          ApiResponses responses) {
        this.registration = registration;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record SessionWebRequest(String username, String password) {
    }

    @PostMapping("/auth/sessions")
    public ResponseEntity<Envelope> createSession(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SessionWebRequest body) {
        var outcome = idempotency.execute(
                new IdempotencyScope("anonymous", "POST", SESSIONS_PATH),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        (body.username() == null ? "" : body.username()) + ""
                                + (body.password() == null ? "" : body.password())),
                SuccessCodes.of("createSession"),
                IssuedSession.class,
                () -> registration.createSession(body.username(), body.password()));
        return responses.forResult(outcome.result(), outcome.code(),
                issued -> Map.of(
                        "accessToken", issued.token().value(),
                        "expiresAt", issued.expiresAt().toString(),
                        "actor", Map.of(
                                "accountId", issued.actor().accountId().asString(),
                                "role", issued.actor().role().name(),
                                "displayName", issued.displayName())));
    }

    @DeleteMapping("/auth/sessions/current")
    public ResponseEntity<Envelope> endCurrentSession(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var actor = Actors.actor(request);
        var token = Actors.token(request);
        if (actor == null || token == null) {
            return responses.forResult(
                    com.library.contract.ModuleResult.Rejected.of(
                            com.library.contract.error.StableErrorCode.MLS_ID_001.code(), "未认证：需要登录"),
                    com.library.contract.error.StableErrorCode.MLS_ID_001.code(),
                    Actors.actorId(request), SESSIONS_PATH + "/current", null, null);
        }
        var outcome = idempotency.execute(
                new IdempotencyScope(actor.accountId().asString(), "DELETE", SESSIONS_PATH + "/current"),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(token.value()),
                SuccessCodes.of("endCurrentSession"),
                SessionEnded.class,
                () -> registration.endCurrentSession(token));
        return responses.forResult(outcome.result(), outcome.code(),
                ended -> Map.of("ended", true));
    }
}
