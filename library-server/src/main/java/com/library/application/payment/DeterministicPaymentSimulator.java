package com.library.application.payment;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
import com.library.settlement.contract.OrderCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受控测试操作员的确定性模拟支付调度（RA-0013 §4：回调模拟）。
 * 调度被接受后由本组件自行投递通知：delayMs=0 同步投递，否则经单线程
 * 延迟执行器投递；duplicateCount 次重复通知沿用同一 eventId，复现网关
 * 重发并依赖 applyCallback 的 eventId 幂等（首次有效通知至多结算一次）。
 * 调度被接受即登记成功审计（DEF-M6-011），关联对象为 orderId、操作者为发起调度的账户。
 */
@Component
public final class DeterministicPaymentSimulator implements PaymentSimulatorPort {
    private static final Logger log = LoggerFactory.getLogger(DeterministicPaymentSimulator.class);

    // A fresh process namespace prevents sequence reuse from colliding with persisted callbacks.
    private final String eventNamespace = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();
    private final OrderCommands orders;
    private final AuditAppender audit;
    private final ScheduledExecutorService deferred = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "payment-simulator-delivery");
        t.setDaemon(true);
        return t;
    });

    public DeterministicPaymentSimulator(OrderCommands orders, AuditAppender audit) {
        this.orders = orders;
        this.audit = audit;
    }

    @Override
    public ModuleResult<Simulation> schedule(Command c) {
        if (c.orderId() <= 0
                || !("SUCCESS".equals(c.result()) || "FAILURE".equals(c.result()))
                || c.delayMs() < 0
                || c.duplicateCount() < 0
                || c.duplicateCount() > 20) {
            return ModuleResult.Rejected.of("MLS-PAY-006", "模拟参数无效");
        }
        long simulationId = sequence.incrementAndGet();
        audit.appendWithBusinessFact(AuditEventTypes.SIMULATION_SCHEDULED,
                c.actorId() <= 0 ? null : c.actorId(),
                new BusinessOutcome(c.orderId(), "SCHEDULED"));
        Runnable delivery = () -> deliver(simulationId, c);
        if (c.delayMs() == 0) {
            delivery.run();
        } else {
            deferred.schedule(delivery, c.delayMs(), TimeUnit.MILLISECONDS);
        }
        return ModuleResult.Accepted.of(new Simulation(Long.toString(simulationId), "SCHEDULED"));
    }

    private void deliver(long simulationId, Command c) {
        String eventId = "sim-" + eventNamespace + "-" + simulationId;
        int sends = 1 + c.duplicateCount();
        for (int i = 0; i < sends; i++) {
            try {
                orders.applyCallback(new OrderCommands.PaymentCallback(eventId, c.orderId(), c.result(), Instant.now()));
            } catch (RuntimeException e) {
                // 投递尽力而为：单条通知失败不阻断其余重复通知，也不把
                // 模拟器故障冒充为网关故障——结算侧以事件表为准可核对。
                log.warn("simulated callback {} for order {} failed: {}", eventId, c.orderId(), e.getMessage());
            }
        }
    }
}
