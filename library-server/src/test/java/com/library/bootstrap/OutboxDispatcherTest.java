package com.library.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.ModuleResult;
import com.library.contract.outbox.OutboxRelay;
import com.library.delivery.contract.OutboxConsumer;
import com.library.delivery.internal.DeliveryModuleImpl;
import com.library.delivery.internal.adapter.persistence.InMemoryDeliveryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-M6-012 分发端单元测试：投递成功置 published_at；投递失败只累加尝试并保留待投递；
 * 崩溃后重投不产生第二次业务效果（消费端 event_consumer_receipt 去重）。
 */
class OutboxDispatcherTest {

    private final InMemoryDeliveryStore store = new InMemoryDeliveryStore();
    private final FakeRelay relay = new FakeRelay();
    private final OutboxDispatcher dispatcher =
            new OutboxDispatcher(relay, new DeliveryModuleImpl(store), new ObjectMapper());

    @Test
    void defM6012_pendingEventIsDeliveredOnceAndMarkedPublished() {
        relay.add(1L, "WAITLIST_ASSIGNED", """
                {"readerId":7,"reservationDate":"2026-09-24"}
                """);

        assertThat(dispatcher.dispatchPending()).isEqualTo(1);

        assertThat(relay.published()).containsExactly(1L);
        assertThat(relay.attempts()).isEmpty();
        assertThat(store.notificationCount()).isEqualTo(1);
        assertThat(dispatcher.dispatchPending()).isZero();
    }

    @Test
    void defM6012_failedDeliveryStaysPendingAndIsRetried() {
        // 消费者故障（抛异常）→ 事件必须保持待投递，只累加投递次数
        var deliveredIds = new ArrayList<Long>();
        var failing = new OutboxDispatcher(relay, new FlakyOnceConsumer(deliveredIds),
                new ObjectMapper());
        relay.add(2L, "WAITLIST_ASSIGNED", """
                {"readerId":7,"reservationDate":"2026-09-24"}
                """);

        assertThat(failing.dispatchPending()).isZero();
        assertThat(relay.published()).isEmpty();
        assertThat(relay.attempts()).containsExactly(2L);

        // 消费者恢复后重投同一事件（同一 eventId），投递成功
        assertThat(failing.dispatchPending()).isEqualTo(1);
        assertThat(relay.published()).containsExactly(2L);
        assertThat(deliveredIds).containsExactly(2L, 2L);
    }

    @Test
    void defM6012_redeliveryAfterCrashKeepsSingleConsumerEffect() {
        relay.add(3L, "WAITLIST_ASSIGNED", """
                {"readerId":7,"reservationDate":"2026-09-24"}
                """);
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(store.notificationCount()).isEqualTo(1);

        // 模拟「消费已生效但 publish 标记未提交即崩溃」：清除 published_at 后重投
        relay.unpublish(3L);
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(relay.published()).containsExactly(3L);
        assertThat(store.notificationCount()).isEqualTo(1);
    }

    @Test
    void defM6012_unknownEventTypeIsDeliveredWithoutConsumerEffect() {
        relay.add(4L, "COPY_RETURNED", """
                {"copyId":1,"condition":"INTACT","readerId":7}
                """);

        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(store.notificationCount()).isZero();
        assertThat(relay.published()).containsExactly(4L);
    }

    /** 首次投递抛故障、其后正常返回的消费者桩；记录收到的 eventId 以证明重投的是同一事件。 */
    private static final class FlakyOnceConsumer implements OutboxConsumer {

        private final List<Long> deliveredIds;
        private boolean failing = true;

        FlakyOnceConsumer(List<Long> deliveredIds) {
            this.deliveredIds = deliveredIds;
        }

        @Override
        public ModuleResult<DeliveryReceipt> consume(String consumerId, OutboxEnvelope event) {
            deliveredIds.add(event.eventId());
            if (failing) {
                failing = false;
                throw new IllegalStateException("注入的消费者故障");
            }
            return ModuleResult.Accepted.of(new DeliveryReceipt(event.eventId(), consumerId, false));
        }
    }

    /** 待投递集合的内存替身：published 集合即 outbox_event.published_at。 */
    private static final class FakeRelay implements OutboxRelay {

        private final List<PendingEvent> events = new ArrayList<>();
        private final Set<Long> published = new HashSet<>();
        private final List<Long> attempts = new ArrayList<>();

        void add(long eventId, String eventType, String payloadJson) {
            events.add(new PendingEvent(eventId, "CIRCULATION_TX", 42L, eventType, payloadJson,
                    Instant.parse("2026-09-22T00:00:00Z")));
        }

        void unpublish(long eventId) {
            published.remove(eventId);
        }
        List<Long> published() {
            return List.copyOf(published);
        }

        List<Long> attempts() {
            return List.copyOf(attempts);
        }

        @Override
        public List<PendingEvent> pending(int limit) {
            return events.stream().filter(event -> !published.contains(event.eventId()))
                    .limit(limit).toList();
        }

        @Override
        public void markPublished(long eventId) {
            published.add(eventId);
        }

        @Override
        public void markAttempt(long eventId) {
            attempts.add(eventId);
        }
    }
}
