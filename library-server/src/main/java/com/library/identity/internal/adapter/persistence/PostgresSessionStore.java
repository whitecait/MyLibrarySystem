package com.library.identity.internal.adapter.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class PostgresSessionStore implements SessionStore {

    private final JdbcTemplate jdbc;

    private static final RowMapper<SessionRow> ROW = (rs, i) -> new SessionRow(
            rs.getLong("id"), rs.getLong("account_id"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
            rs.getObject("ended_at", OffsetDateTime.class) == null
                    ? null : rs.getObject("ended_at", OffsetDateTime.class).toInstant());

    public PostgresSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(long accountId, String tokenHash, Instant expiresAt) {
        return jdbc.queryForObject("""
                INSERT INTO auth_session (account_id, token_hash, expires_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, accountId, tokenHash,
                OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC));
    }

    @Override
    public Optional<SessionRow> byTokenHash(String tokenHash) {
        return jdbc.query("SELECT * FROM auth_session WHERE token_hash = ?", ROW, tokenHash)
                .stream().findFirst();
    }

    @Override
    public void end(long sessionId) {
        jdbc.update("UPDATE auth_session SET ended_at = now() WHERE id = ? AND ended_at IS NULL", sessionId);
    }
}
