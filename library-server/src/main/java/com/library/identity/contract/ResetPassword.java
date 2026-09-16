package com.library.identity.contract;

/** 密码重置命令。 */
public record ResetPassword(com.library.contract.model.AccountId target, String reason) {
}
