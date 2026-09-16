package com.library.web.support;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.error.StableErrorCode;
import com.library.web.Envelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Controller 结果映射：ModuleResult → HTTP 信封响应（MD-0003 §1）。
 * 401/403/404 的业务拒绝按 DD-0004 以独立事务登记拒绝审计。
 */
@Component
public class ApiResponses {

    /** 身份类「目标不可见」在 403 语义之外的 404 呈现（MLS-ID-005 双义，见批次报告）。 */
    public static final int NOT_FOUND = 404;

    private final AuditAppender audit;

    public ApiResponses(AuditAppender audit) {
        this.audit = audit;
    }

    public <T> ResponseEntity<Envelope> forResult(ModuleResult<T> result,
                                                  String code,
                                                  Long auditActorId,
                                                  String auditPath,
                                                  Function<T, Object> dataMapper,
                                                  Function<ModuleResult.Rejected<T>, Object> rejectionData) {
        if (result instanceof ModuleResult.Accepted<T> accepted) {
            return ResponseEntity.ok(Envelope.of(code, "OK", dataMapper.apply(accepted.value())));
        }
        if (result instanceof ModuleResult.Rejected<T> rejected) {
            var status = StableErrorCode.httpStatusOf(rejected.code());
            var data = rejectionData == null ? defaultRejectionData(rejected.reasons()) : rejectionData.apply(rejected);
            if (status == 401 || status == 403 || status == 404) {
                audit.append("ACCESS_DENIED", auditActorId,
                        Map.of("path", auditPath == null ? "" : auditPath,
                                "code", rejected.code(), "status", status));
            }
            return ResponseEntity.status(status)
                    .body(Envelope.of(rejected.code(), message(rejected), data));
        }
        throw new IllegalStateException("未知 ModuleResult 形态");
    }

    /** 带审计上下文、默认拒绝 data 的映射。 */
    public <T> ResponseEntity<Envelope> forResult(ModuleResult<T> result,
                                                  String code,
                                                  Long auditActorId,
                                                  String auditPath,
                                                  Function<T, Object> dataMapper) {
        return forResult(result, code, auditActorId, auditPath, dataMapper, null);
    }

    public <T> ResponseEntity<Envelope> forResult(ModuleResult<T> result, String code,
                                                  Function<T, Object> dataMapper) {
        return forResult(result, code, null, null, dataMapper, null);
    }

    public static Object defaultRejectionData(List<String> reasons) {
        return Map.of("reasons", reasons);
    }

    private static String message(ModuleResult.Rejected<?> rejected) {
        var reasons = rejected.reasons();
        return reasons.isEmpty() ? rejected.code() : reasons.get(0);
    }
}
