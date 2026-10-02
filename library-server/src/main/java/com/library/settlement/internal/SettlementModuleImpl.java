package com.library.settlement.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.security.ActorContext;
import com.library.contract.time.BusinessClock;
import com.library.settlement.contract.*;
import com.library.settlement.contract.model.FineId;
import com.library.settlement.contract.model.MoneyCents;
import com.library.settlement.internal.adapter.persistence.SettlementStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SETTLEMENT 模块实现（B2：欠费快照与归还罚款形成；订单/回调/退款/对账随 B3）。
 */
@Component
public class SettlementModuleImpl implements DebtQuery, FineCommands, FineQuery, OrderCommands,
        OrderQuery, RefundCommands, ReconciliationCommands {

    private final SettlementStore store;
    private final BusinessClock clock;

    public SettlementModuleImpl(SettlementStore store, BusinessClock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public ModuleResult<DebtSnapshot> debtFor(com.library.identity.contract.model.ReaderId readerId) {
        var summary = store.debtFor(readerId.value())
                .orElse(new SettlementStore.DebtSummary(0, 0));
        return ModuleResult.Accepted.of(new DebtSnapshot(readerId.value(),
                summary.dueFineCount(), summary.dueCents()));
    }

    @Override
    public ModuleResult<Fine> createFine(FineFromReturn command) {
        if (command.amount().value() < 0) {
            return ModuleResult.Rejected.of(com.library.contract.error.StableErrorCode.MLS_PAY_007.code(),
                    "罚款金额不可为负");
        }
        var existing = store.fineBySourceTx(command.sourceTxId());
        if (existing.isPresent()) {
            // 同归还重复计罚幂等吸收（BR-0019 数据库唯一约束 + 用例层返回首笔）
            return ModuleResult.Accepted.of(toFine(existing.get()));
        }
        store.insertFine(new SettlementStore.NewFine(command.readerId(),
                command.sourceTxId(), command.amount().value(), command.reason()));
        return ModuleResult.Accepted.of(toFine(store.fineBySourceTx(command.sourceTxId()).orElseThrow()));
    }

    @Override
    public ModuleResult<FinePage> fines(FineCriteria query) {
        var page = query.page() == null ? com.library.contract.model.PageCursor.of(null, null) : query.page();
        var afterId = page.cursor() == null ? 0L : Long.parseLong(page.cursor());
        var rows = store.listFines(query.readerId(), query.status(), afterId, page.limit() + 1);
        var hasMore = rows.size() > page.limit();
        var visible = rows.stream().limit(page.limit()).map(this::toFineView).toList();
        return ModuleResult.Accepted.of(new FinePage(visible,
                hasMore ? Long.toString(visible.get(visible.size() - 1).fineId()) : null));
    }

    @Override
    public ModuleResult<Order> createOrder(CreateOrder command) {
        var fine = store.fineById(command.fineId().value());
        if (fine.isEmpty() || fine.get().readerId() != command.readerId()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(), "罚款不可见");
        }
        if (!"DUE".equals(fine.get().status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_002.code(), "罚款已结清");
        }
        if (store.unreleasedOrder(command.fineId().value()).isPresent()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_001.code(), "罚款已有占用订单");
        }
        var expiresAt = clock.now().plus(Duration.ofMinutes(15));
        var id = store.insertOrder(command.fineId().value(), fine.get().amountCents(), expiresAt);
        return ModuleResult.Accepted.of(toOrder(store.orderById(id).orElseThrow()));
    }

    @Override
    @Transactional
    public ModuleResult<Callback> applyCallback(PaymentCallback command) {
        store.lockCallbackEvent(command.eventId());
        var prior = store.callback(command.eventId());
        if (prior.isPresent()) {
            var value = prior.get();
            if (value.orderId() != command.orderId() || !value.result().equals(command.result())) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_003.code(), "回调事件内容冲突");
            }
            return ModuleResult.Accepted.of(new Callback(value.eventId(), value.orderId(), value.outcome()));
        }
        var order = store.lockOrderById(command.orderId());
        if (order.isEmpty()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(), "订单不可见");
        }
        String outcome;
        if ("SUCCESS".equals(command.result()) && "OCCUPIED".equals(order.get().status())) {
            if (store.transitionOrder(command.orderId(), "OCCUPIED", "PAID", clock.now())) {
                store.settleFine(order.get().fineId(), clock.now());
                outcome = "PAID";
            } else {
                var current = store.orderById(command.orderId()).orElseThrow();
                if ("PAID".equals(current.status())) outcome = "PAID";
                else if ("CLOSED".equals(current.status())) outcome = "CONFLICT_RECORDED";
                else return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_004.code(), "支付回调状态已变化");
            }
        } else if ("SUCCESS".equals(command.result()) && "PAID".equals(order.get().status())) {
            outcome = "PAID";
        } else if ("SUCCESS".equals(command.result()) && "CLOSED".equals(order.get().status())) {
            outcome = "CONFLICT_RECORDED";
        } else {
            outcome = "FAILURE_RECORDED";
        }
        var row = new SettlementStore.CallbackRow(command.eventId(), command.orderId(), command.result(),
                command.occurredAt(), outcome);
        store.insertCallback(row);
        return ModuleResult.Accepted.of(new Callback(command.eventId(), command.orderId(), outcome));
    }

    @Override
    @Transactional
    public ModuleResult<Order> closeOrder(CloseOrder command) {
        var order = store.lockOrderById(command.orderId());
        if (order.isEmpty()) return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(), "订单不可见");
        var actor = command.actor();
        if (actor == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "请先登录");
        }
        if (actor.role() == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "无权关闭支付单");
        }
        var fine = store.fineById(order.get().fineId());
        if (fine.isEmpty()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(), "订单不可见");
        }
        boolean canClose = switch (actor.role()) {
            case READER -> actor.accountId() != null && fine.get().readerId() == actor.accountId().value();
            case LIBRARIAN, ADMIN, SYSTEM -> true;
        };
        if (!canClose) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(), "订单不可见");
        }
        if ("OCCUPIED".equals(order.get().status())) {
            if (!store.transitionOrder(command.orderId(), "OCCUPIED", "CLOSED", clock.now())) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_004.code(), "关闭提交失败");
            }
        }
        return ModuleResult.Accepted.of(toOrder(store.orderById(command.orderId()).orElseThrow()));
    }

    @Override
    public ModuleResult<Order> order(long orderId, ActorContext actor) {
        var row=store.orderById(orderId);
        if(row.isEmpty()) return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(),"订单不可见");
        var fine=store.fineById(row.get().fineId());
        if(actor.role()==com.library.contract.model.Role.READER && (fine.isEmpty()||fine.get().readerId()!=actor.accountId().value()))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_005.code(),"订单不可见");
        return ModuleResult.Accepted.of(toOrder(row.get()));
    }

    @Override
    public ModuleResult<OrderQuery.OrderPage> occupiedOrders(
            com.library.contract.model.PageCursor page, ActorContext actor) {
        if (actor == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "请先登录");
        }
        if (actor.role() != com.library.contract.model.Role.READER) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "仅读者可查询自己的占用订单");
        }
        var cursor = page == null ? com.library.contract.model.PageCursor.first() : page;
        var afterId = cursor.cursor() == null ? 0L : Long.parseLong(cursor.cursor());
        var rows = store.listOccupiedOrders(actor.accountId().value(), afterId, cursor.limit() + 1);
        var hasMore = rows.size() > cursor.limit();
        var visible = rows.stream().limit(cursor.limit()).map(this::toOrder).toList();
        var nextCursor = hasMore && !visible.isEmpty()
                ? Long.toString(visible.get(visible.size() - 1).orderId()) : null;
        return ModuleResult.Accepted.of(new OrderQuery.OrderPage(visible, nextCursor));
    }

    @Override
    public ModuleResult<Refund> refund(ApprovedRefundCommand command) {
        return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_009.code(), "退款功能已下线");
    }

    @Override
    public ModuleResult<Reconciliation> reconcile(ReconciliationCommand command) {
        return ModuleResult.Rejected.of(StableErrorCode.MLS_PAY_009.code(), "对账功能已下线");
    }

    private Fine toFine(SettlementStore.FineRow row) {
        // DB DUE → API UNPAID（枚举三方一致，OD-0002 §17）
        return new Fine(FineId.of(row.id()), row.readerId(), row.amountCents(), apiFineStatus(row.status()), row.createdAt());
    }

    private FineView toFineView(SettlementStore.FineRow row) {
        return new FineView(row.id(), row.readerId(), row.amountCents(), apiFineStatus(row.status()), row.createdAt());
    }

    private String apiFineStatus(String status) {
        return switch (status) { case "DUE" -> "UNPAID"; case "SETTLED" -> "PAID"; default -> status; };
    }

    private Order toOrder(SettlementStore.OrderRow row) {
        return new Order(row.id(), FineId.of(row.fineId()), row.amountCents(), row.status(), row.expiresAt(), row.version());
    }
}
