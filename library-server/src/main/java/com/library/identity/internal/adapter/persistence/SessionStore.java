package com.library.identity.internal.adapter.persistence;

import java.time.Instant;
import java.util.Optional;

/**
 * 会话持久化内部缝：opaque 令牌只存 SHA-256 摘要；吊销以 ended_at 表达。
 */
public interface SessionStore {

    long insert(long accountId, String tokenHash, Instant expiresAt);

    Optional<SessionRow> byTokenHash(String tokenHash);

    /** 吊销（幂等：已结束的会话保持首次结束时间）。 */
    void end(long sessionId);

    record SessionRow(long id, long accountId, Instant createdAt, Instant expiresAt, Instant endedAt) {
    }
}
