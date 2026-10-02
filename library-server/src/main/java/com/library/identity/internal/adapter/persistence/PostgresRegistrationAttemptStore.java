package com.library.identity.internal.adapter.persistence;

import com.library.application.registration.RegistrationAttemptStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalInt;

/** PostgreSQL UPSERT serializes requests for the same browser session across server instances. */
@Repository
public class PostgresRegistrationAttemptStore implements RegistrationAttemptStore {

    private final JdbcTemplate jdbc;

    public PostgresRegistrationAttemptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int issueSessionAndRecord(String sessionId) {
        var hash = sha256(sessionId);
        return jdbc.queryForObject("""
                INSERT INTO registration_session_attempt(session_hash, window_started_at, request_count, updated_at)
                VALUES (?, now(), 1, now())
                RETURNING request_count
                """, Integer.class, hash);
    }

    @Override
    public OptionalInt recordRequest(String sessionId) {
        var hash = sha256(sessionId);
        var counts = jdbc.query("""
                UPDATE registration_session_attempt SET
                    window_started_at = CASE
                        WHEN window_started_at <= now() - interval '1 hour'
                        THEN now() ELSE window_started_at END,
                    request_count = CASE
                        WHEN window_started_at <= now() - interval '1 hour'
                        THEN 1 ELSE request_count + 1 END,
                    updated_at = now()
                WHERE session_hash = ?
                RETURNING request_count
                """, (rs, rowNum) -> rs.getInt(1), hash);
        return counts.isEmpty() ? OptionalInt.empty() : OptionalInt.of(counts.get(0));
    }

    @Override
    public void purgeExpired() {
        jdbc.update("DELETE FROM registration_session_attempt WHERE updated_at < now() - interval '2 hours'");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
