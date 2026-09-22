package com.library.contract.outbox;

import java.time.Instant;
import java.util.List;

/**
 * 待投递 Outbox 的读取与投递标记（DD-0003 §1 第 7 条：分发器实现属 M5）。
 * 分发器按 `published_at IS NULL` 扫描（索引 `ix_outbox_unpublished`）；投递成功置
 * `published_at`，投递失败只累加 `publish_attempts` 并保留待投递状态，使进程崩溃或
 * 消费者故障后由下一轮重投（至少一次投递）。
 */
public interface OutboxRelay {

    /** 待投递事件，按 id 升序（登记顺序即投递顺序）。 */
    List<PendingEvent> pending(int limit);

    void markPublished(long eventId);

    void markAttempt(long eventId);

    record PendingEvent(long eventId, String aggregateType, long aggregateId, String eventType,
                        String payloadJson, Instant createdAt) {
    }
}
