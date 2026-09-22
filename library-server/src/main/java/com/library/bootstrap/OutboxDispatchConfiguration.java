package com.library.bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox 分发循环触发（DEF-M6-012）。分发器本体与触发解耦：{@link OutboxDispatcher#dispatchPending()}
 * 可被测试直接调用，后台轮询仅在 `library.outbox.dispatch-enabled=true`（默认）时装配——
 * 集成测试以 `library.outbox.dispatch-enabled=false` 关闭后台线程，避免与断言竞争。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "library.outbox", name = "dispatch-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxDispatchConfiguration {

    @Bean
    OutboxDispatchLoop outboxDispatchLoop(OutboxDispatcher dispatcher) {
        return new OutboxDispatchLoop(dispatcher);
    }

    /** 固定延迟轮询；单次失败不终止循环（下一轮重投）。 */
    static class OutboxDispatchLoop {

        private final OutboxDispatcher dispatcher;

        OutboxDispatchLoop(OutboxDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Scheduled(fixedDelayString = "${library.outbox.dispatch-interval-ms:2000}",
                initialDelayString = "${library.outbox.dispatch-initial-delay-ms:3000}")
        void poll() {
            dispatcher.dispatchPending();
        }
    }
}
