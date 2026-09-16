package com.library.identity.contract;

/** 会话令牌（opaque，仅原文出现在创建响应中；服务端只存 SHA-256 摘要）。 */
public record SessionToken(String value) {

    public SessionToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("session token 不可为空");
        }
    }
}
