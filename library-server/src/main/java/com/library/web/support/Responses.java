package com.library.web.support;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.web.Envelope;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Controller 结果映射助手：ModuleResult → HTTP 信封响应。
 * 成功恒为 200（MD-0003 §1）；业务拒绝按 StableErrorCode 登记的状态映射。
 */
public final class Responses {

    private Responses() {
    }

    /**
     * @param dataMapper    Accepted 值 → OpenAPI *Data 形状
     * @param rejectionData 业务拒绝时的 data（默认 reasons 列表；冲突样例可给出目标摘要）
     */
    public static <T> ResponseEntity<Envelope> forResult(ModuleResult<T> result,
                                                         String code,
                                                         Function<T, Object> dataMapper,
                                                         Function<ModuleResult.Rejected<T>, Object> rejectionData) {
        if (result instanceof ModuleResult.Accepted<T> accepted) {
            return ResponseEntity.ok(Envelope.of(code, "OK", dataMapper.apply(accepted.value())));
        }
        if (result instanceof ModuleResult.Rejected<T> rejected) {
            var status = StableErrorCode.httpStatusOf(rejected.code());
            var data = rejectionData == null ? defaultRejectionData(rejected.reasons()) : rejectionData.apply(rejected);
            return ResponseEntity.status(status)
                    .body(Envelope.of(rejected.code(), message(rejected), data));
        }
        throw new IllegalStateException("未知 ModuleResult 形态");
    }

    public static <T> ResponseEntity<Envelope> forResult(ModuleResult<T> result,
                                                         String code,
                                                         Function<T, Object> dataMapper) {
        return forResult(result, code, dataMapper, null);
    }

    public static Object defaultRejectionData(List<String> reasons) {
        return Map.of("reasons", reasons);
    }

    private static String message(ModuleResult.Rejected<?> rejected) {
        var reasons = rejected.reasons();
        return reasons.isEmpty() ? rejected.code() : reasons.get(0);
    }
}
