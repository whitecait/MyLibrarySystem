package com.library.settlement;

import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.time.FixedBusinessClock;
import com.library.settlement.contract.FineCommands;
import com.library.settlement.contract.OrderCommands;
import com.library.settlement.contract.RefundCommands;
import com.library.settlement.contract.ReconciliationCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.settlement.contract.model.MoneyCents;
import com.library.settlement.internal.SettlementModuleImpl;
import com.library.settlement.internal.adapter.persistence.InMemorySettlementStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementModuleTest {

    @Test
    void utPay001_unpaidFineCreatesOneOccupiedOrder() {
        var store = new InMemorySettlementStore();
        var module = new SettlementModuleImpl(store,
                new FixedBusinessClock(Instant.parse("2026-09-17T01:00:00Z")));
        var fine = module.createFine(new FineCommands.FineFromReturn(
                41, 7, MoneyCents.of(1200), "逾期"));
        var fineId = ((ModuleResult.Accepted<FineCommands.Fine>) fine).value().fineId();

        var result = module.createOrder(new OrderCommands.CreateOrder(fineId, 7));

        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        var order = ((ModuleResult.Accepted<OrderCommands.Order>) result).value();
        assertThat(order.fineId()).isEqualTo(fineId);
        assertThat(order.amountCents()).isEqualTo(1200);
        assertThat(order.status()).isEqualTo("OCCUPIED");
        assertThat(order.expiresAt()).isEqualTo(Instant.parse("2026-09-17T01:15:00Z"));
    }

    @Test void utPay002_secondUnreleasedOrderIsRejected() {
        var fixture=fixture(); fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7));
        var result=fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7));
        assertRejected(result,"MLS-PAY-001");
    }

    @Test void utPay003_successCallbackSettlesFineAndClosesCreationPath() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        var callback=fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-1",order.orderId(),"SUCCESS",fixture.clock.now()));
        assertThat(accepted(callback).outcome()).isEqualTo("PAID");
        assertRejected(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)),"MLS-PAY-002");
    }

    @Test void utPay004_failureCallbackDoesNotReleaseOrder() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-f",order.orderId(),"FAILURE",fixture.clock.now()));
        assertRejected(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)),"MLS-PAY-001");
    }

    @Test void utPay012_occupiedOrderQueryIsReaderScopedAndExcludesTerminalOrders() {
        var fixture = fixture();
        var reader = new ActorContext(AccountId.of(7), Role.READER, Instant.parse("2026-09-17T01:00:00Z"));
        var otherReader = new ActorContext(AccountId.of(8), Role.READER, Instant.parse("2026-09-17T01:00:00Z"));
        var admin = new ActorContext(AccountId.of(9), Role.ADMIN, Instant.parse("2026-09-17T01:00:00Z"));

        var failedOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback(
                "evt-owned-failure", failedOrder.orderId(), "FAILURE", fixture.clock.now()));

        var secondFine = fineFor(fixture.module, 42, 7);
        var secondOccupied = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(secondFine, 7)));
        var closedFine = fineFor(fixture.module, 43, 7);
        var closedOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(closedFine, 7)));
        fixture.module.closeOrder(new OrderCommands.CloseOrder(closedOrder.orderId(), reader));
        var paidFine = fineFor(fixture.module, 44, 7);
        var paidOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(paidFine, 7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback(
                "evt-owned-paid", paidOrder.orderId(), "SUCCESS", fixture.clock.now()));

        var otherFine = fineFor(fixture.module, 45, 8);
        var otherOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(otherFine, 8)));

        var firstPage = accepted(fixture.module.occupiedOrders(PageCursor.of(null, 1), reader));
        assertThat(firstPage.items()).extracting(OrderCommands.Order::orderId)
                .containsExactly(failedOrder.orderId());
        assertThat(firstPage.nextCursor()).isNotNull();
        var secondPage = accepted(fixture.module.occupiedOrders(PageCursor.of(firstPage.nextCursor(), 1), reader));
        assertThat(secondPage.items()).extracting(OrderCommands.Order::orderId)
                .containsExactly(secondOccupied.orderId());
        assertThat(secondPage.nextCursor()).isNull();

        var otherPage = accepted(fixture.module.occupiedOrders(PageCursor.first(), otherReader));
        assertThat(otherPage.items()).extracting(OrderCommands.Order::orderId)
                .containsExactly(otherOrder.orderId());
        assertRejected(fixture.module.occupiedOrders(PageCursor.first(), admin), "MLS-ID-005");
    }

    @Test void utPay005_duplicateCallbackReturnsFirstOutcome() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        var command=new OrderCommands.PaymentCallback("evt-d",order.orderId(),"SUCCESS",fixture.clock.now());
        assertThat(accepted(fixture.module.applyCallback(command))).isEqualTo(accepted(fixture.module.applyCallback(command)));
    }

    @Test void utPay006_changedDuplicateCallbackIsConflictEvidence() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-c",order.orderId(),"FAILURE",fixture.clock.now()));
        assertRejected(fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-c",order.orderId(),"SUCCESS",fixture.clock.now())),"MLS-PAY-003");
    }

    @Test void utPay007_closedOrderReleasesFineForSuccessor() {
        var fixture=fixture(); var first=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        var owner = new ActorContext(AccountId.of(7), Role.READER, fixture.clock.now());
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId(), owner))).status()).isEqualTo("CLOSED");
        assertThat(accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7))).orderId()).isNotEqualTo(first.orderId());
    }

    @Test void utPay008_paidOrderAbsorbsLateClose() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-p",order.orderId(),"SUCCESS",fixture.clock.now()));
        var owner = new ActorContext(AccountId.of(7), Role.READER, fixture.clock.now());
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(order.orderId(), owner))).status()).isEqualTo("PAID");
    }

    @Test
    void utPay013_closeAuthorizationPrecedesMutationAndTerminalReplay() {
        var fixture = fixture();
        var owner = new ActorContext(AccountId.of(7), Role.READER, fixture.clock.now());
        var otherReader = new ActorContext(AccountId.of(8), Role.READER, fixture.clock.now());
        var librarian = new ActorContext(AccountId.of(9), Role.LIBRARIAN, fixture.clock.now());
        var admin = new ActorContext(AccountId.of(10), Role.ADMIN, fixture.clock.now());
        var system = new ActorContext(AccountId.of(11), Role.SYSTEM, fixture.clock.now());

        var first = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        assertRejected(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId(), otherReader)),
                "MLS-PAY-005");
        assertThat(accepted(fixture.module.order(first.orderId(), owner)).status()).isEqualTo("OCCUPIED");
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId(), owner))).status())
                .isEqualTo("CLOSED");
        assertRejected(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId(), otherReader)),
                "MLS-PAY-005");
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId(), owner))).status())
                .isEqualTo("CLOSED");

        var librarianOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(librarianOrder.orderId(), librarian)))
                .status()).isEqualTo("CLOSED");
        var adminOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(adminOrder.orderId(), admin)))
                .status()).isEqualTo("CLOSED");
        var systemOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(systemOrder.orderId(), system)))
                .status()).isEqualTo("CLOSED");

        var paidOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId, 7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback(
                "evt-pay-before-close-check", paidOrder.orderId(), "SUCCESS", fixture.clock.now()));
        assertRejected(fixture.module.closeOrder(new OrderCommands.CloseOrder(paidOrder.orderId(), otherReader)),
                "MLS-PAY-005");
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(paidOrder.orderId(), owner)))
                .status()).isEqualTo("PAID");

        var explicitFine = fineFor(fixture.module, 46, 7);
        var explicitActorOrder = accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(explicitFine, 7)));
        assertRejected(fixture.module.closeOrder(new OrderCommands.CloseOrder(explicitActorOrder.orderId(), null)),
                "MLS-ID-001");
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(explicitActorOrder.orderId(), owner)))
                .status()).isEqualTo("CLOSED");
    }

    @Test void utPay009_refundCommandIsDisabledWithoutStoreEffects() {
        var store = new InMemorySettlementStore();
        var module = new SettlementModuleImpl(store,
                new FixedBusinessClock(Instant.parse("2026-09-17T01:00:00Z")));
        var before = store.refundCount();

        assertRejected(module.refund(new RefundCommands.ApprovedRefundCommand(1, 500, 4)), "MLS-PAY-009");
        assertThat(store.refundCount()).isEqualTo(before);
    }

    @Test void utPay010_reconciliationCommandIsDisabledWithoutStoreEffects() {
        var store = new InMemorySettlementStore();
        var module = new SettlementModuleImpl(store,
                new FixedBusinessClock(Instant.parse("2026-09-17T01:00:00Z")));
        var before = store.reconciliationCount();

        assertRejected(module.reconcile(new ReconciliationCommands.ReconciliationCommand(
                BusinessDate.parse("2026-09-17"))), "MLS-PAY-009");
        assertThat(store.reconciliationCount()).isEqualTo(before);
    }

    @Test void utPay011_fineListUsesPublicStatusVocabulary() {
        var fixture=fixture();
        var page=accepted(fixture.module.fines(new com.library.settlement.contract.FineQuery.FineCriteria(7L,"UNPAID",com.library.contract.model.PageCursor.first())));
        assertThat(page.items()).singleElement().extracting(com.library.settlement.contract.FineQuery.FineView::status).isEqualTo("UNPAID");
    }

    private Fixture fixture() {
        var store=new InMemorySettlementStore();
        var clock=new FixedBusinessClock(Instant.parse("2026-09-17T01:00:00Z"));
        var module=new SettlementModuleImpl(store,clock);
        var fine=accepted(module.createFine(new FineCommands.FineFromReturn(41,7,MoneyCents.of(1200),"逾期")));
        return new Fixture(module,clock,fine.fineId());
    }

    private static com.library.settlement.contract.model.FineId fineFor(
            SettlementModuleImpl module, long sourceTxId, long readerId) {
        return accepted(module.createFine(new FineCommands.FineFromReturn(
                sourceTxId, readerId, MoneyCents.of(1200), "逾期"))).fineId();
    }

    @SuppressWarnings("unchecked") private static <T> T accepted(ModuleResult<T> result) { return ((ModuleResult.Accepted<T>)result).value(); }
    private static void assertRejected(ModuleResult<?> result,String code) { assertThat(result).isInstanceOf(ModuleResult.Rejected.class); assertThat(((ModuleResult.Rejected<?>)result).code()).isEqualTo(code); }
    private record Fixture(SettlementModuleImpl module,FixedBusinessClock clock,com.library.settlement.contract.model.FineId fineId) {}
}
