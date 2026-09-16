package com.library.contract.idempotency;

/**
 * 幂等作用域（MD-0003 §1）：认证主体 + HTTP 方法 + 规范路径。
 * 与客户端幂等键共同唯一确定一次请求效果。
 */
public record IdempotencyScope(String subjectId, String method, String canonicalPath) {

    public IdempotencyScope {
        if (method == null || method.isBlank() || canonicalPath == null || canonicalPath.isBlank()) {
            throw new IllegalArgumentException("幂等作用域字段不可为空");
        }
        if (subjectId == null || subjectId.isBlank()) {
            subjectId = "anonymous";
        }
    }
}
