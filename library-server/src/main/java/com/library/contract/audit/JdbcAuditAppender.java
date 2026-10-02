package com.library.contract.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.InfrastructureFailure;
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
            var json = jsonOf(detail);
            // 失败审计独立提交（DD-0004 §1）；登记失败只记日志，不影响主响应
            var independent = new TransactionTemplate(transactions.getTransactionManager());
            independent.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
            independent.executeWithoutResult(status -> insert(eventType, actorId, json));
        } catch (Exception e) {
            log.warn("审计登记失败: {}", eventType, e);
        }
    }

    @Override
    public void appendWithBusinessFact(String eventType, Long actorId, Object detail) {
        try {
            // 成功审计加入当前事务（JdbcTemplate 复用线程绑定的连接）；
            // 普通成功路径保持历史 best-effort 语义。
            insert(eventType, actorId, jsonOf(detail));
        } catch (Exception e) {
            log.warn("成功路径审计登记失败: {}", eventType, e);
        }
    }

    @Override
    public void appendRequiredWithBusinessFact(String eventType, Long actorId, Object detail) {
        try {
            insert(eventType, actorId, jsonOf(detail));
        } catch (Exception e) {
            throw new InfrastructureFailure("必需业务审计登记失败: " + eventType, e);
        }
    }

    private String jsonOf(Object detail) throws Exception {
        return mapper.writeValueAsString(detail == null ? java.util.Map.of() : detail);
    }

    private void insert(String eventType, Long actorId, String json) {
        jdbc.update("""
                INSERT INTO audit_event (occurred_at, actor_id, event_type, detail)
                VALUES (now(), ?, ?, ?::jsonb)
                """, actorId == null ? 0L : actorId, eventType, json);
    }
}
