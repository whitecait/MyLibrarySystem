package com.library.identity.internal.adapter.persistence;

/**
 * IDENTITY 持久化内部缝（MLS-DOC-MD-0004 §4）：唯一性、插入与角色/状态读取。
 * 生产 PostgreSQL Adapter 与测试 Adapter 实现同一接口；跨模块不得导入本包。
 */
public interface AccountStore {

    boolean usernameExists(String username);

    /** @throws DuplicateUsername username 唯一约束竞争（并发注册，IT-ID-001） */
    StoredAccount insert(NewAccount candidate);

    record NewAccount(String username, String passwordHash, String displayName) {
    }

    record StoredAccount(long id, String username, String displayName, String role,
                         String status, long version) {
    }

    final class DuplicateUsername extends RuntimeException {
        public DuplicateUsername(String username) {
            super("duplicate username: " + username);
        }
    }
}
