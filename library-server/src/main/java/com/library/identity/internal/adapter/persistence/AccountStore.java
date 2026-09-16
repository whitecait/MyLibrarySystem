package com.library.identity.internal.adapter.persistence;

import java.util.List;
import java.util.Optional;

/**
 * IDENTITY 持久化内部缝（MLS-DOC-MD-0004 §4）：账户唯一性、版本条件更新、
 * 角色与状态读取。生产 PostgreSQL Adapter 与测试 Adapter 实现同一接口。
 */
public interface AccountStore {

    boolean usernameExists(String username);

    /** @throws DuplicateUsername username 唯一约束竞争（并发注册，IT-ID-001） */
    StoredAccount insert(NewAccount candidate);

    Optional<StoredAccount> byUsername(String username);

    Optional<StoredAccount> byId(long accountId);

    /** id 升序、afterId 之后、limit 上限（键集分页）。 */
    List<StoredAccount> list(String role, String status, long afterId, int limit);

    /** 馆员直办的读者状态变更（ACTIVE/SUSPENDED）；返回新版本；目标必须存在且为 READER。 */
    long updateReaderStatus(long accountId, String nextStatus);

    /** 密码重置写入；返回新版本。 */
    long updatePasswordHash(long accountId, String passwordHash);

    record NewAccount(String username, String passwordHash, String displayName) {
    }

    record StoredAccount(long id, String username, String passwordHash, String displayName,
                         String role, String status, long version) {
    }

    final class DuplicateUsername extends RuntimeException {
        public DuplicateUsername(String username) {
            super("duplicate username: " + username);
        }
    }
}
