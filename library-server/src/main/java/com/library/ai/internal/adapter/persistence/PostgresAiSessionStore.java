package com.library.ai.internal.adapter.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.contract.*;
import com.library.contract.InfrastructureFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresAiSessionStore implements AiSessionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PostgresAiSessionStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public StoredSession create(AiSessionOwner owner, String role, Instant now) {
        try {
            Long id = owner instanceof AccountAiOwner a
                    ? jdbc.queryForObject("INSERT INTO ai_session(account_id,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?) RETURNING id", Long.class, a.accountId().value(), timestamp(now), timestamp(now))
                    : jdbc.queryForObject("INSERT INTO ai_session(visitor_owner_digest,status,created_at,updated_at) VALUES (?,'ACTIVE',?,?) RETURNING id", Long.class, ((VisitorAiOwner) owner).digest().value(), timestamp(now), timestamp(now));
            return new StoredSession(id, owner instanceof AccountAiOwner a ? a.accountId().value() : null,
                    owner instanceof VisitorAiOwner v ? v.digest().value() : null, role, "ACTIVE");
        } catch (Exception e) { throw new InfrastructureFailure("AI 会话持久化失败", e); }
    }

    @Override public Optional<StoredSession> session(long id) {
        return jdbc.query("SELECT id,account_id,visitor_owner_digest,status FROM ai_session WHERE id=?", (rs,n) ->
                new StoredSession(rs.getLong("id"), (Long) rs.getObject("account_id"),
                        rs.getString("visitor_owner_digest"), null, rs.getString("status")), id).stream().findFirst();
    }
    @Override public boolean ownedBy(StoredSession s, AiSessionOwner owner) {
        return owner instanceof AccountAiOwner a ? s.accountId() != null && s.accountId() == a.accountId().value()
                : s.visitorDigest() != null && s.visitorDigest().equals(((VisitorAiOwner) owner).digest().value());
    }
    @Override public long appendMessage(long sessionId, String role, String content, Map<String,Object> payload, Instant now) {
        return jdbc.queryForObject("INSERT INTO ai_message(session_id,role,content,tool_payload,created_at) VALUES (?,?,?,?::jsonb,?) RETURNING id",
                Long.class, sessionId, role, content, write(payload), timestamp(now));
    }
    @Override public List<StoredMessage> messagesBefore(long sessionId, long beforeMessageId, int limit) {
        return jdbc.query("SELECT id,role,content FROM ai_message WHERE session_id=? AND id<? AND role IN ('USER','ASSISTANT') ORDER BY id DESC LIMIT ?",
                (rs,n) -> new StoredMessage(rs.getLong("id"), rs.getString("role"), rs.getString("content")),
                sessionId, beforeMessageId, limit);
    }
    @Override public long appendEvent(long sessionId, String type, Map<String,Object> payload, Instant now) {
        return jdbc.queryForObject("INSERT INTO ai_event(session_id,event_type,payload,occurred_at) VALUES (?,?,?::jsonb,?) RETURNING id",
                Long.class, sessionId, type, write(payload), timestamp(now));
    }
    @Override public List<AiEvent> events(long sessionId, long afterId, int limit) {
        return jdbc.query("SELECT id,event_type,payload,occurred_at FROM ai_event WHERE session_id=? AND id>? ORDER BY id LIMIT ?", (rs,n) ->
                new AiEvent(rs.getLong("id"), rs.getString("event_type"), read(rs.getString("payload")),
                        rs.getTimestamp("occurred_at").toInstant()), sessionId, afterId, limit);
    }
    @Override public boolean tryConsume(AiSessionOwner owner, LocalDate date, int limit) {
        if (owner instanceof VisitorAiOwner) {
            return !jdbc.query("INSERT INTO ai_visitor_quota_usage(usage_date,model_calls,version) VALUES (?,1,1) ON CONFLICT(usage_date) DO UPDATE SET model_calls=ai_visitor_quota_usage.model_calls+1,version=ai_visitor_quota_usage.version+1,updated_at=now() WHERE ai_visitor_quota_usage.model_calls < ? RETURNING model_calls",
                    (rs,n)->rs.getInt(1), date, limit).isEmpty();
        }
        long id = ((AccountAiOwner) owner).accountId().value();
        return !jdbc.query("INSERT INTO ai_quota_usage(account_id,usage_date,model_calls,version) VALUES (?,?,1,1) ON CONFLICT(account_id,usage_date) DO UPDATE SET model_calls=ai_quota_usage.model_calls+1,version=ai_quota_usage.version+1,updated_at=now() WHERE ai_quota_usage.model_calls < ? RETURNING model_calls",
                (rs,n)->rs.getInt(1), id, date, limit).isEmpty();
    }
    @Override public void refund(AiSessionOwner owner, LocalDate date) {
        if (owner instanceof VisitorAiOwner) jdbc.update("UPDATE ai_visitor_quota_usage SET model_calls=GREATEST(model_calls-1,0),version=version+1,updated_at=now() WHERE usage_date=?", date);
        else jdbc.update("UPDATE ai_quota_usage SET model_calls=GREATEST(model_calls-1,0),version=version+1,updated_at=now() WHERE account_id=? AND usage_date=?", ((AccountAiOwner) owner).accountId().value(), date);
    }
    @Override public int remaining(AiSessionOwner owner, LocalDate date, int limit) {
        Integer used = owner instanceof VisitorAiOwner
                ? jdbc.query("SELECT model_calls FROM ai_visitor_quota_usage WHERE usage_date=?", (rs,n)->rs.getInt(1), date).stream().findFirst().orElse(0)
                : jdbc.query("SELECT model_calls FROM ai_quota_usage WHERE account_id=? AND usage_date=?", (rs,n)->rs.getInt(1), ((AccountAiOwner) owner).accountId().value(), date).stream().findFirst().orElse(0);
        return Math.max(0, limit - used);
    }
    @Override public void status(long sessionId, String status, Instant now) { jdbc.update("UPDATE ai_session SET status=?,updated_at=? WHERE id=?", status, timestamp(now), sessionId); }
    private String write(Map<String,Object> value) { try { return json.writeValueAsString(value); } catch(Exception e){ throw new InfrastructureFailure("AI JSON 编码失败",e); } }
    private Map<String,Object> read(String value) { try { return json.readValue(value, new TypeReference<>(){}); } catch(Exception e){ throw new InfrastructureFailure("AI JSON 解码失败",e); } }
    private java.sql.Timestamp timestamp(Instant value) { return java.sql.Timestamp.from(value); }
}
