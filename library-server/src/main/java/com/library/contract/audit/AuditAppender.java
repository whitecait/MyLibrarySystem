package com.library.contract.audit;

/**
 * 审计事件登记。生产写 audit_event 表，区分两类事务语义（DD-0004 §1）：
 * 失败审计（{@link #append}）以 REQUIRES_NEW 独立事务提交，不随业务回滚丢弃（SC-Q5/Q9）；
 * 成功路径审计（{@link #appendWithBusinessFact}）加入当前业务事务，与业务事实同提交同回滚
 * ——「批准记录、目标业务变更与待投递事件同时可见或同时不存在」（AT-0002 §2.29）同样适用于
 * 成功审计与所登记的业务事实。
 * 审计查询属 DELIVERY（B3）；登记端口为共享机制。
 */
public interface AuditAppender {

    /**
     * 失败审计：独立事务提交，登记失败只记日志，不影响主响应。
     *
     * @param eventType 审计事件类型（词表见 {@link AuditEventTypes}）
     * @param actorId   操作者账户 id；无操作者（自动判定、匿名）传 {@code null}
     * @param detail    可序列化明细；成功路径应使用 {@link BusinessOutcome}
     */
    void append(String eventType, Long actorId, Object detail);

    /**
     * 成功路径审计：加入当前业务事务（无事务时按单语句提交）。
     * 仅供已形成业务事实的成功用例调用。
     */
    void appendWithBusinessFact(String eventType, Long actorId, Object detail);
}
