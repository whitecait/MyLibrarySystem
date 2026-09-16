package com.library.web;

import com.library.contract.idempotency.IdempotencyScope;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.RegisterReader;
import com.library.application.registration.RegistrationUseCases;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpenAPI 操作 registerReader（POST /auth/registrations，PUBLIC）。
 * B0 契约探针：请求/响应/状态码/幂等头按 OpenAPI 校验（CONTRACT-003），
 * 共同样例 AUTH-REGISTER-SUCCESS 为权威输入。
 */
@RestController
@RequestMapping("/api/v1")
public class RegistrationController {

    public static final String PATH = "/api/v1/auth/registrations";
    static final String FIELD_ERROR_CODE = com.library.contract.error.StableErrorCode.MLS_ID_003.code();

    private final RegistrationUseCases registration;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final com.library.web.support.ApiResponses responses;

    public RegistrationController(RegistrationUseCases registration,
                                  com.library.contract.idempotency.IdempotencyService idempotency,
                                  com.library.web.support.ApiResponses responses) {
        this.registration = registration;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record RegistrationWebRequest(String username, String password, String displayName) {
    }

    @PostMapping("/auth/registrations")
    public ResponseEntity<Envelope> register(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RegistrationWebRequest body) {
        Actors.declareFieldErrorCode(request, FIELD_ERROR_CODE);
        var command = new RegisterReader(body.username(), body.password(), body.displayName());
        var outcome = idempotency.execute(
                new IdempotencyScope("anonymous", "POST", PATH),
                idempotencyKey,
                requestHash(body),
                SuccessCodes.of("registerReader"),
                AccountRegistration.class,
                () -> registration.register(command));
        return responses.forResult(outcome.result(), outcome.code(),
                reg -> Map.of(
                        "accountId", reg.accountId().asString(),
                        "role", reg.role().name(),
                        "status", reg.status().name()));
    }

    private String requestHash(RegistrationWebRequest body) {
        return com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                (body.username() == null ? "" : body.username()) + "" +
                (body.password() == null ? "" : body.password()) + "" +
                (body.displayName() == null ? "" : body.displayName()));
    }
}
