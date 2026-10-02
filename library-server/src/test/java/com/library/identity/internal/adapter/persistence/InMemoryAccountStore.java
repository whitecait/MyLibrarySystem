package com.library.identity.internal.adapter.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用 AccountStore Adapter（单元层）：只实现接口语义，
 * 不冒充 PostgreSQL 唯一约束并发语义（那由 IT-ID-001 真库证明）。
 */
public class InMemoryAccountStore implements AccountStore {

    private final Map<String, StoredAccount> byUsername = new HashMap<>();
    private long sequence;

    @Override
    public boolean usernameExists(String username) {
        return byUsername.containsKey(username);
    }

    @Override
    public StoredAccount insert(NewAccount candidate) {
        if (byUsername.containsKey(candidate.username())) {
            throw new DuplicateUsername(candidate.username());
        }
        var stored = new StoredAccount(++sequence, candidate.username(), candidate.passwordHash(),
                candidate.displayName(), "READER", "ACTIVE", 0);
        byUsername.put(candidate.username(), stored);
        return stored;
    }

    @Override
    public Optional<StoredAccount> byUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    @Override
    public Optional<StoredAccount> byId(long accountId) {
        return byUsername.values().stream().filter(a -> a.id() == accountId).findFirst();
    }

    @Override
    public List<StoredAccount> list(String role, String status, long afterId, int limit) {
        var rows = new ArrayList<>(byUsername.values()).stream()
                .filter(a -> a.id() > afterId)
                .filter(a -> role == null || a.role().equals(role))
                .filter(a -> status == null || a.status().equals(status))
                .sorted(java.util.Comparator.comparingLong(StoredAccount::id))
                .limit(limit)
                .toList();
        return rows;
    }

    @Override
    public long updateReaderStatus(long accountId, String nextStatus) {
        var current = byId(accountId).orElseThrow();
        var updated = new StoredAccount(current.id(), current.username(), current.passwordHash(),
                current.displayName(), current.role(), nextStatus, current.version() + 1);
        byUsername.put(updated.username(), updated);
        return updated.version();
    }

    @Override
    public Long updateReviewedState(long accountId, long expectedVersion, String nextStatus, String nextRole) {
        var current = byId(accountId).orElse(null);
        if (current == null || current.version() != expectedVersion) {
            return null;
        }
        var updated = new StoredAccount(current.id(), current.username(), current.passwordHash(),
                current.displayName(), nextRole, nextStatus, current.version() + 1);
        byUsername.put(updated.username(), updated);
        return updated.version();
    }

    @Override
    public long updatePasswordHash(long accountId, String passwordHash) {
        var current = byId(accountId).orElseThrow();
        var updated = new StoredAccount(current.id(), current.username(), passwordHash,
                current.displayName(), current.role(), current.status(), current.version() + 1);
        byUsername.put(updated.username(), updated);
        return updated.version();
    }
}
