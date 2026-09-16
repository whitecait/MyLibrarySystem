package com.library.identity.internal.adapter.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 测试用 SessionStore Adapter（单元层）。 */
public class InMemorySessionStore implements SessionStore {

    private final Map<String, SessionRow> byHash = new HashMap<>();
    private long sequence;
    private Instant now = Instant.EPOCH;

    /** 单元测试推进当前时间（过期判定用）。 */
    public void tickTo(Instant instant) {
        this.now = instant;
    }

    @Override
    public long insert(long accountId, String tokenHash, Instant expiresAt) {
        var row = new SessionRow(++sequence, accountId, now, expiresAt, null);
        byHash.put(tokenHash, row);
        return row.id();
    }

    @Override
    public Optional<SessionRow> byTokenHash(String tokenHash) {
        return Optional.ofNullable(byHash.get(tokenHash));
    }

    @Override
    public void end(long sessionId) {
        byHash.values().stream().filter(r -> r.id() == sessionId && r.endedAt() == null)
                .findFirst()
                .ifPresent(row -> byHash.put(toStringKey(row),
                        new SessionRow(row.id(), row.accountId(), row.createdAt(), row.expiresAt(), now)));
    }

    private String toStringKey(SessionRow row) {
        return byHash.entrySet().stream()
                .filter(e -> e.getValue().id() == row.id())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }
}
