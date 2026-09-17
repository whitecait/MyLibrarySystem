package com.library.ai.contract;

public record VisitorOwnerDigest(String value) {
    public VisitorOwnerDigest {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("访客所有者摘要必须是 SHA-256 小写十六进制");
        }
    }
}
