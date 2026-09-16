package com.library.identity.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresAccountStore implements AccountStore {

    private final JdbcTemplate jdbc;

    public PostgresAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean usernameExists(String username) {
        var count = jdbc.queryForObject(
                "SELECT count(*) FROM account WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    @Override
    public StoredAccount insert(NewAccount candidate) {
        // ON CONFLICT DO NOTHING：并发同用户名时按冲突返回（IT-ID-001），
        // 避免约束错误中止当前 PostgreSQL 事务影响用例层拒绝路径
        List<Long> inserted = jdbc.queryForList("""
                INSERT INTO account (username, password_hash, display_name, role, status)
                VALUES (?, ?, ?, 'READER', 'ACTIVE')
                ON CONFLICT (username) DO NOTHING
                RETURNING id
                """, Long.class, candidate.username(), candidate.passwordHash(), candidate.displayName());
        if (inserted.isEmpty()) {
            throw new DuplicateUsername(candidate.username());
        }
        return new StoredAccount(inserted.get(0), candidate.username(), candidate.displayName(), "READER", "ACTIVE", 0);
    }
}
