package com.library.contract.audit;

/**
 * 审计事件登记（DD-0004：失败审计独立提交）。生产写 audit_event 表，
 * 以 REQUIRES_NEW 独立事务提交，不受业务事务回滚影响。
 * 审计查询属 DELIVERY（B3）；登记端口为共享机制。
 */
public interface AuditAppender {

    void append(String eventType, Long actorId, Object detail);
}
