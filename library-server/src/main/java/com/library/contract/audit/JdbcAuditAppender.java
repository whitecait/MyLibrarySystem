package com.library.contract.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcAuditAppender implements AuditAppender {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JdbcAuditAppender.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public JdbcAuditAppender(JdbcTemplate jdbc, ObjectMapper mapper, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override
    public void append(String eventType, Long actorId, Object detail) {
        try {
            var json = mapper.writeValueAsString(detail == null ? java.util.Map.of() : detail);
            // 失败审计独立提交（DD-0004 §1）；登记失败只记日志，不影响主响应
            var independent = new TransactionTemplate(transactions.getTransactionManager());
            independent.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
            independent.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO audit_event (occurred_at, actor_id, event_type, detail)
                    VALUES (now(), ?, ?, ?::jsonb)
                    """, actorId == null ? 0L : actorId, eventType, json));
        } catch (Exception e) {
            log.warn("审计登记失败: {}", eventType, e);
        }
    }
}
