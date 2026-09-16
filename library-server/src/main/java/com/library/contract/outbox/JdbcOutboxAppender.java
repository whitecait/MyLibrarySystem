package com.library.contract.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.InfrastructureFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOutboxAppender implements OutboxAppender {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcOutboxAppender(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public long append(String aggType, long aggId, String eventType, Object payload) {
        try {
            var json = mapper.writeValueAsString(payload == null ? java.util.Map.of() : payload);
            return jdbc.queryForObject("""
                    INSERT INTO outbox_event (agg_type, agg_id, event_type, payload)
                    VALUES (?::varchar, ?, ?::varchar, ?::jsonb)
                    RETURNING id
                    """, Long.class, aggType, aggId, eventType, json);
        } catch (Exception e) {
            throw new InfrastructureFailure("Outbox 事件登记失败", e);
        }
    }
}
