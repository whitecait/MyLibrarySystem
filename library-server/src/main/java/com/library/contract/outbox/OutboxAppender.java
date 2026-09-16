package com.library.contract.outbox;

/**
 * 事务性 Outbox 登记（DD-0004 §1）：业务事实与事件同事务写入 outbox_event。
 * 实现必须加入当前事务，不得自行提交；分发与消费回执属 DELIVERY。
 */
public interface OutboxAppender {

    /**
     * @param aggType   聚合类型（如 IDENTITY_ACCOUNT、CIRCULATION_TX）
     * @param aggId     聚合行标识
     * @param eventType 事件类型（模块内登记的稳定名）
     * @param payload   事件负载（可 Jackson 序列化对象）
     * @return outbox_event.id
     */
    long append(String aggType, long aggId, String eventType, Object payload);
}
