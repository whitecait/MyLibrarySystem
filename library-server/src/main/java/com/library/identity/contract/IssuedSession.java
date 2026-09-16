package com.library.identity.contract;

/**
 * 创建会话结果（OpenAPI SessionData：accessToken/expiresAt/actor{accountId,role,displayName}）。
 */
public record IssuedSession(SessionToken token,
                            java.time.Instant expiresAt,
                            com.library.contract.security.ActorContext actor,
                            String displayName) {
}
