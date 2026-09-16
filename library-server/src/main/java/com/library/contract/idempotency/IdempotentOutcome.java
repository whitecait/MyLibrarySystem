package com.library.contract.idempotency;

import com.library.contract.ModuleResult;

/**
 * 幂等执行结果：
 * - FRESH：本请求首次执行业务（code = successCode 或业务拒绝码）；
 * - REPLAYED：同键同摘要，返回首次结果快照（X2：两响应一致）；
 * - CONFLICT：同键不同摘要，业务拒绝 {@code MLS-IDEMP-002}（409）。
 */
public record IdempotentOutcome<T>(Kind kind, String code, ModuleResult<T> result) {

    public enum Kind { FRESH, REPLAYED, CONFLICT }

    public boolean replayed() {
        return kind == Kind.REPLAYED;
    }
}
