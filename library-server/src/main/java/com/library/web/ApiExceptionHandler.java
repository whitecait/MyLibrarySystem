package com.library.web;

import com.library.contract.InfrastructureFailure;
import com.library.contract.error.StableErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一错误信封映射（MD-0003 §1）：基础设施失败与请求结构错误在此收口，
 * 业务拒绝由各 Controller 经 Responses 助手映射、不抛异常。
 * 字段错误码由 Controller 通过 request attribute「fieldErrorCode」声明；
 * 未声明时退回 MLS-SYS-001。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public static final String FIELD_ERROR_CODE_ATTRIBUTE = ApiExceptionHandler.class.getName() + ".fieldErrorCode";

    @ExceptionHandler(InfrastructureFailure.class)
    public ResponseEntity<Envelope> infrastructureFailure(InfrastructureFailure failure) {
        return ResponseEntity.status(StableErrorCode.MLS_SYS_001.httpStatus())
                .body(Envelope.of(StableErrorCode.MLS_SYS_001.code(), "系统失败", java.util.Map.of()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Envelope> missingHeader(MissingRequestHeaderException e) {
        var code = fieldErrorCodeOrDefault();
        return ResponseEntity.badRequest()
                .body(Envelope.of(code, "缺少必需请求头 " + e.getHeaderName(),
                        java.util.Map.of("reasons", java.util.List.of(e.getHeaderName() + " required"))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Envelope> unreadableBody(HttpMessageNotReadableException e) {
        var code = fieldErrorCodeOrDefault();
        return ResponseEntity.badRequest()
                .body(Envelope.of(code, "请求体不可读", java.util.Map.of("reasons", java.util.List.of("malformed body"))));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Envelope> unexpected(Throwable e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Envelope.of(StableErrorCode.MLS_SYS_001.code(), "系统失败", java.util.Map.of()));
    }

    private String fieldErrorCodeOrDefault() {
        var attr = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attr instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object declared = servlet.getRequest().getAttribute(FIELD_ERROR_CODE_ATTRIBUTE);
            if (declared instanceof String code && !code.isBlank()) {
                return code;
            }
        }
        return StableErrorCode.MLS_SYS_001.code();
    }
}
