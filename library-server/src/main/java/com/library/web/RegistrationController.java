package com.library.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.idempotency.JdbcIdempotencyService;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.RegisterReader;
import com.library.application.registration.RegistrationUseCases;
import com.library.web.support.Responses;
import com.library.web.support.SuccessCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final ObjectMapper mapper;

    public RegistrationController(RegistrationUseCases registration,
                                  com.library.contract.idempotency.IdempotencyService idempotency,
                                  ObjectMapper mapper) {
        this.registration = registration;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    public record RegistrationWebRequest(String username, String password, String displayName) {
    }

    @PostMapping("/auth/registrations")
    public ResponseEntity<Envelope> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RegistrationWebRequest body) {
        declareFieldErrorCode();
        var command = new RegisterReader(body.username(), body.password(), body.displayName());
        var outcome = idempotency.execute(
                new IdempotencyScope("anonymous", "POST", PATH),
                idempotencyKey,
                requestHash(body),
                SuccessCodes.of("registerReader"),
                AccountRegistration.class,
                () -> registration.register(command));
        return Responses.forResult(outcome.result(), outcome.code(),
                reg -> java.util.Map.of(
                        "accountId", reg.accountId().asString(),
                        "role", reg.role().name(),
                        "status", reg.status().name()));
    }

    private String requestHash(RegistrationWebRequest body) {
        return JdbcIdempotencyService.sha256(
                (body.username() == null ? "" : body.username()) + "" +
                (body.password() == null ? "" : body.password()) + "" +
                (body.displayName() == null ? "" : body.displayName()));
    }

    private void declareFieldErrorCode() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            servlet.getRequest().setAttribute(com.library.web.ApiExceptionHandler.FIELD_ERROR_CODE_ATTRIBUTE, FIELD_ERROR_CODE);
        }
    }
}
