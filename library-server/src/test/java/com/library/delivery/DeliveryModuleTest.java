package com.library.delivery;

import com.library.contract.ModuleResult;
import com.library.delivery.contract.OutboxConsumer;
import com.library.delivery.contract.*;
import com.library.contract.model.*;
import com.library.contract.security.ActorContext;
import com.library.delivery.internal.DeliveryModuleImpl;
import com.library.delivery.internal.adapter.persistence.InMemoryDeliveryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryModuleTest {
    @Test void utDel001_notificationsAreNearestFirstAndUnreadFilterIsStable() {
        var store=new InMemoryDeliveryStore();var module=new DeliveryModuleImpl(store);
        module.consume("notification",event(1,41,"WAITLIST_ASSIGNED",LocalDate.of(2026,9,20)));
        module.consume("notification",event(2,42,"WAITLIST_ASSIGNED",LocalDate.of(2026,9,18)));
        var page=accepted(module.notifications(new NotificationQuery.NotificationCriteria(true,PageCursor.first()),actor(7,Role.READER)));
        assertThat(page.items()).extracting(NotificationQuery.Notification::relatedTxId).containsExactly(42L,41L);
    }
    @Test void utDel002_visitAndNoVisitAreMutuallyExclusivePerTransaction() {
        var store=new InMemoryDeliveryStore();var module=new DeliveryModuleImpl(store);
        module.consume("notification",event(1,41,"WAITLIST_ASSIGNED",LocalDate.of(2026,9,18)));
        module.consume("notification",event(2,41,"WAITLIST_EXPIRED",LocalDate.of(2026,9,18)));
        var page=accepted(module.notifications(new NotificationQuery.NotificationCriteria(false,PageCursor.first()),actor(7,Role.READER)));
        assertThat(page.items()).singleElement().extracting(NotificationQuery.Notification::type).isEqualTo("NO_VISIT");
    }
    @Test void utDel003_onlyOwnerCanMarkNotificationRead() {
        var store=new InMemoryDeliveryStore();var module=new DeliveryModuleImpl(store);module.consume("notification",event(1,41,"WAITLIST_ASSIGNED",LocalDate.of(2026,9,18)));
        var note=accepted(module.notifications(new NotificationQuery.NotificationCriteria(false,PageCursor.first()),actor(7,Role.READER))).items().getFirst();
        assertThat(module.markRead(note.notificationId(),actor(8,Role.READER))).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(accepted(module.markRead(note.notificationId(),actor(7,Role.READER))).read()).isTrue();
    }
    @Test void utDel004_auditQueryFiltersAndRequiresStaffRole() {
        var store=new InMemoryDeliveryStore();store.addAudit(new com.library.delivery.internal.adapter.persistence.DeliveryStore.AuditRow(1,"ORDER_PAID",7L,9L,"SUCCESS",Instant.parse("2026-09-17T00:00:00Z")));
        var module=new DeliveryModuleImpl(store);var criteria=new AuditQuery.AuditCriteria(9L,null,"ORDER_PAID",PageCursor.first());
        assertThat(module.audit(criteria,actor(7,Role.READER))).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(accepted(module.audit(criteria,actor(3,Role.LIBRARIAN))).items()).hasSize(1);
    }
    @Test void utDel005_duplicateDeliveryHasOneConsumerEffect() {
        var store=new InMemoryDeliveryStore(); var module=new DeliveryModuleImpl(store);
        var event=new OutboxConsumer.OutboxEnvelope(11,"CIRCULATION_TX",42,"WAITLIST_ASSIGNED",
                Map.of("readerId",7L,"reservationDate", LocalDate.of(2026,9,18)),Instant.parse("2026-09-17T01:00:00Z"));
        assertThat(accepted(module.consume("notification",event)).duplicate()).isFalse();
        assertThat(accepted(module.consume("notification",event)).duplicate()).isTrue();
        assertThat(store.notificationCount()).isEqualTo(1);
    }
    @SuppressWarnings("unchecked") private static <T>T accepted(ModuleResult<T> r){return ((ModuleResult.Accepted<T>)r).value();}
    private static ActorContext actor(long id,Role role){return new ActorContext(AccountId.of(id),role,Instant.parse("2026-09-17T00:00:00Z"));}
    private static OutboxConsumer.OutboxEnvelope event(long id,long tx,String type,LocalDate date){return new OutboxConsumer.OutboxEnvelope(id,"CIRCULATION_TX",tx,type,Map.of("readerId",7L,"reservationDate",date),Instant.parse("2026-09-17T01:00:00Z"));}
}
