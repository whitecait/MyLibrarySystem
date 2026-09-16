package com.library.identity.internal.adapter.persistence;

import java.util.HashMap;
import java.util.Map;

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
        var stored = new StoredAccount(++sequence, candidate.username(), candidate.displayName(),
                "READER", "ACTIVE", 0);
        byUsername.put(candidate.username(), stored);
        return stored;
    }
}
