package com.library.settlement;

import com.library.contract.ModuleResult;
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
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(first.orderId()))).status()).isEqualTo("CLOSED");
        assertThat(accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7))).orderId()).isNotEqualTo(first.orderId());
    }

    @Test void utPay008_paidOrderAbsorbsLateClose() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-p",order.orderId(),"SUCCESS",fixture.clock.now()));
        assertThat(accepted(fixture.module.closeOrder(new OrderCommands.CloseOrder(order.orderId()))).status()).isEqualTo("PAID");
    }

    @Test void utPay009_refundRejectsUnpaidOrExcessAmount() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        assertRejected(fixture.module.refund(new RefundCommands.ApprovedRefundCommand(order.orderId(),1201,4)),"MLS-PAY-007");
    }

    @Test void utPay010_approvedRefundRequiresPaidOrder() {
        var fixture=fixture(); var order=accepted(fixture.module.createOrder(new OrderCommands.CreateOrder(fixture.fineId,7)));
        fixture.module.applyCallback(new OrderCommands.PaymentCallback("evt-r",order.orderId(),"SUCCESS",fixture.clock.now()));
        assertThat(accepted(fixture.module.refund(new RefundCommands.ApprovedRefundCommand(order.orderId(),500,4))).status()).isEqualTo("APPROVED");
    }

    @Test void utPay011_fineListUsesPublicStatusVocabulary() {
        var fixture=fixture();
        var page=accepted(fixture.module.fines(new com.library.settlement.contract.FineQuery.FineCriteria(7L,"UNPAID",com.library.contract.model.PageCursor.first())));
        assertThat(page.items()).singleElement().extracting(com.library.settlement.contract.FineQuery.FineView::status).isEqualTo("UNPAID");
    }

    @Test void utPay012_reconciliationIsUniquePerBusinessDate() {
        var fixture=fixture(); var command=new ReconciliationCommands.ReconciliationCommand(BusinessDate.parse("2026-09-17"));
        assertThat(fixture.module.reconcile(command)).isInstanceOf(ModuleResult.Accepted.class);
        assertRejected(fixture.module.reconcile(command),"MLS-PAY-008");
    }

    private Fixture fixture() {
        var store=new InMemorySettlementStore();
        var clock=new FixedBusinessClock(Instant.parse("2026-09-17T01:00:00Z"));
        var module=new SettlementModuleImpl(store,clock);
        var fine=accepted(module.createFine(new FineCommands.FineFromReturn(41,7,MoneyCents.of(1200),"逾期")));
        return new Fixture(module,clock,fine.fineId());
    }

    @SuppressWarnings("unchecked") private static <T> T accepted(ModuleResult<T> result) { return ((ModuleResult.Accepted<T>)result).value(); }
    private static void assertRejected(ModuleResult<?> result,String code) { assertThat(result).isInstanceOf(ModuleResult.Rejected.class); assertThat(((ModuleResult.Rejected<?>)result).code()).isEqualTo(code); }
    private record Fixture(SettlementModuleImpl module,FixedBusinessClock clock,com.library.settlement.contract.model.FineId fineId) {}
}
