package com.library.contract.security;

import com.library.contract.model.AccountId;
import com.library.contract.model.Role;

import java.time.Instant;

/**
 * 认证主体上下文（MLS-DOC-MD-0004 §2）：只由 IDENTITY 的 ActorResolver 产生，
 * 调用方不得自行构造更高权限的实例。匿名主体不产生本对象（见 SubjectRef）。
 */
public record ActorContext(AccountId accountId, Role role, Instant authenticatedAt) {

    public ActorContext {
        if (accountId == null || role == null || authenticatedAt == null) {
            throw new IllegalArgumentException("ActorContext 字段不可为空");
        }
    }

    public SubjectRef subjectRef() {
        return SubjectRef.account(accountId.asString());
    }
}
