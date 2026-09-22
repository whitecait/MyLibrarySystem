package com.library.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.ModuleResult;
import com.library.contract.outbox.OutboxRelay;
import com.library.delivery.contract.OutboxConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 应用内 Outbox 分发器（DEF-M6-012；DD-0003 §1 第 7 条、M5 实施计划 B3「Outbox 分发及消费者回执」）。
 *
 * <p>扫描待投递事件（`published_at IS NULL`）逐条投递给消费者：投递被接受则置 `published_at`，
 * 否则只累加 `publish_attempts` 并保留待投递状态。崩溃后重投由下一轮扫描自然完成——事件在
 * 消费侧以 `event_consumer_receipt(consumer_id,event_id)` 与消费效果同事务去重，故重投不产生
 * 第二次业务效果（M5 计划 B3-GATE「同消费者同事件效果唯一，不同消费者/事件互不吞并」）。
 *
 * <p>置于 `bootstrap`：模块内不得引用 delivery 聚合接口（ModuleDependencyRulesTest
 * aggregateModuleInterfacesAreImportedOnlyByBootstrap）；本类只依赖非聚合的 {@link OutboxConsumer} 契约。
 */
@Component
public class OutboxDispatcher {

    /** 通知消费者标识；写入 `event_consumer_receipt.consumer_id`，同一事件对该消费者至多一次效果。 */
    public static final String NOTIFICATION_CONSUMER = "notification";

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_LIMIT = 100;

    private final OutboxRelay relay;
    private final OutboxConsumer consumer;
    private final ObjectMapper mapper;

    public OutboxDispatcher(OutboxRelay relay, OutboxConsumer consumer, ObjectMapper mapper) {
        this.relay = relay;
        this.consumer = consumer;
        this.mapper = mapper;
    }

    /**
     * 投递当前一批待投递事件。
     *
     * @return 本轮被消费者接受并置为已投递的事件数
     */
    public int dispatchPending() {
        var delivered = 0;
        for (var event : relay.pending(BATCH_LIMIT)) {
            if (deliver(event)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(OutboxRelay.PendingEvent event) {
        try {
            var envelope = new OutboxConsumer.OutboxEnvelope(event.eventId(), event.aggregateType(),
                    event.aggregateId(), event.eventType(), payloadOf(event), event.createdAt());
            var result = consumer.consume(NOTIFICATION_CONSUMER, envelope);
            if (result instanceof ModuleResult.Accepted<?>) {
                relay.markPublished(event.eventId());
                return true;
            }
            relay.markAttempt(event.eventId());
            return false;
        } catch (RuntimeException failure) {
            // 只记录异常类型与关联 id，不记录负载内容（证据脱敏口径）
            log.warn("Outbox 投递失败 eventId={} eventType={} cause={}",
                    event.eventId(), event.eventType(), failure.getClass().getSimpleName());
            relay.markAttempt(event.eventId());
            return false;
        }
    }

    private Map<String, Object> payloadOf(OutboxRelay.PendingEvent event) {
        try {
            return mapper.readValue(event.payloadJson(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception parseFailure) {
            throw new IllegalStateException("Outbox 负载不可解析", parseFailure);
        }
    }
}
