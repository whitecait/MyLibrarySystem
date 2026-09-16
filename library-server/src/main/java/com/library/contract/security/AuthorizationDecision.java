package com.library.contract.security;

/**
 * 授权判定结果值。
 */
public record AuthorizationDecision(boolean allowed, String reason) {

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(true, null);
    }

    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(false, reason);
    }
}
