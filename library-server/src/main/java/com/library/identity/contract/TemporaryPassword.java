package com.library.identity.contract;

/**
 * 密码重置结果（OpenAPI PasswordResetData：temporaryPassword/forceChange=true）。
 */
public record TemporaryPassword(String value, boolean forceChange) {
}
