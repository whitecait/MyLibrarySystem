package com.library.contract.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class JdbcOutboxRelay implements OutboxRelay {

    private static final RowMapper<PendingEvent> ROW = (rs, i) -> new PendingEvent(
            rs.getLong("id"), rs.getString("agg_type"), rs.getLong("agg_id"),
            rs.getString("event_type"), rs.getString("payload"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    public JdbcOutboxRelay(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PendingEvent> pending(int limit) {
        return jdbc.query("""
                SELECT id, agg_type, agg_id, event_type, payload::text AS payload, created_at
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT ?
                """, ROW, limit);
    }

    @Override
    public void markPublished(long eventId) {
        jdbc.update("UPDATE outbox_event SET published_at=now() WHERE id=?", eventId);
    }

    @Override
    public void markAttempt(long eventId) {
        jdbc.update("UPDATE outbox_event SET publish_attempts=publish_attempts+1 WHERE id=?", eventId);
    }
}
