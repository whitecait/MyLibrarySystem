package com.library.identity.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresAccountStore implements AccountStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<AccountStore.StoredAccount> ROW = (rs, i) ->
            new AccountStore.StoredAccount(rs.getLong("id"), rs.getString("username"),
                    rs.getString("password_hash"), rs.getString("display_name"),
                    rs.getString("role"), rs.getString("status"), rs.getLong("version"));

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
        return new StoredAccount(inserted.get(0), candidate.username(), candidate.passwordHash(),
                candidate.displayName(), "READER", "ACTIVE", 0);
    }

    @Override
    public Optional<StoredAccount> byUsername(String username) {
        return jdbc.query("SELECT * FROM account WHERE username = ?", ROW, username).stream().findFirst();
    }

    @Override
    public Optional<StoredAccount> byId(long accountId) {
        return jdbc.query("SELECT * FROM account WHERE id = ?", ROW, accountId).stream().findFirst();
    }

    @Override
    public List<StoredAccount> list(String role, String status, long afterId, int limit) {
        var sql = new StringBuilder("SELECT * FROM account WHERE id > ?");
        var args = new java.util.ArrayList<Object>();
        args.add(afterId);
        if (role != null && !role.isBlank()) {
            sql.append(" AND role = ?");
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY id LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    @Override
    public long updateReaderStatus(long accountId, String nextStatus) {
        return jdbc.queryForObject("""
                UPDATE account SET status = ?, version = version + 1, updated_at = now()
                WHERE id = ? RETURNING version
                """, Long.class, nextStatus, accountId);
    }

    @Override
    public Long updateReviewedState(long accountId, long expectedVersion, String nextStatus, String nextRole) {
        var versions = jdbc.queryForList("""
                UPDATE account SET status = ?, role = ?, version = version + 1, updated_at = now()
                WHERE id = ? AND version = ? RETURNING version
                """, Long.class, nextStatus, nextRole, accountId, expectedVersion);
        return versions.isEmpty() ? null : versions.getFirst();
    }

    @Override
    public long updatePasswordHash(long accountId, String passwordHash) {
        return jdbc.queryForObject("""
                UPDATE account SET password_hash = ?, version = version + 1, updated_at = now()
                WHERE id = ? RETURNING version
                """, Long.class, passwordHash, accountId);
    }
}
