package com.library.web.support;

import java.util.Map;

/**
 * 操作成功稳定码目录（operationId → MLS-&lt;MOD&gt;-S###）。
 * 共同样例中出现的码为权威值；其余操作按模块内登记顺序编号，
 * 随批次实现逐操作登记。OpenAPI 对成功 code 只约束为字符串，
 * 本目录是后端单一登记点，前端不依赖未出现在样例中的具体值。
 */
public final class SuccessCodes {

    private static final Map<String, String> BY_OPERATION = Map.of(
            // B0 契约探针（共同样例 AUTH-REGISTER-SUCCESS 权威值）
            "registerReader", "MLS-ID-S001"
    );

    private SuccessCodes() {
    }

    public static String of(String operationId) {
        var code = BY_OPERATION.get(operationId);
        if (code == null) {
            throw new IllegalStateException("操作尚未登记成功码: " + operationId);
        }
        return code;
    }
}
