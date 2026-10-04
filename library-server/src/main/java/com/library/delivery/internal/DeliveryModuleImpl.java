package com.library.delivery.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.TxId;
import com.library.delivery.contract.*;
import com.library.delivery.internal.adapter.persistence.DeliveryStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Component
public class DeliveryModuleImpl implements DeliveryModule {
    private final DeliveryStore store;
    public DeliveryModuleImpl(DeliveryStore store){this.store=store;}

    @Override public ModuleResult<NotificationPage> notifications(NotificationCriteria q,ActorContext actor){var page=q.page()==null?com.library.contract.model.PageCursor.first():q.page();var after=page.cursor()==null?0:Long.parseLong(page.cursor());var rows=store.notifications(actor.accountId().value(),q.unreadOnly(),after,page.limit()+1);var more=rows.size()>page.limit();var items=rows.stream().limit(page.limit()).map(this::notification).toList();return ModuleResult.Accepted.of(new NotificationPage(items,more?Long.toString(items.get(items.size()-1).notificationId()):null));}
    @Override public ModuleResult<java.util.List<Notification>> notificationsForTransactions(java.util.List<TxId> transactionIds,ActorContext actor){var ids=transactionIds.stream().map(TxId::value).toList();var rows=store.notificationsForTransactions(actor.accountId().value(),ids);return ModuleResult.Accepted.of(rows.stream().map(this::notification).toList());}
    @Override public ModuleResult<ReadReceipt> markRead(long id,ActorContext actor){if(!store.markRead(id,actor.accountId().value(),Instant.now()))return ModuleResult.Rejected.of(StableErrorCode.MLS_DEL_001.code(),"通知不可见");return ModuleResult.Accepted.of(new ReadReceipt(true));}
    @Override public ModuleResult<AuditPage> audit(AuditCriteria q,ActorContext actor){if(actor.role()!=Role.LIBRARIAN&&actor.role()!=Role.ADMIN)return ModuleResult.Rejected.of("MLS-AUTH-002","无审计查询权限");var page=q.page()==null?com.library.contract.model.PageCursor.first():q.page();var after=page.cursor()==null?0:Long.parseLong(page.cursor());var rows=store.audits(q.relatedId(),q.actorId(),q.eventType(),after,page.limit()+1);var more=rows.size()>page.limit();var items=rows.stream().limit(page.limit()).map(r->new AuditQuery.AuditEvent(r.id(),r.eventType(),r.actorId(),r.relatedId(),r.outcome(),r.occurredAt())).toList();return ModuleResult.Accepted.of(new AuditPage(items,more?Long.toString(items.get(items.size()-1).eventId()):null));}
    @Override @Transactional public ModuleResult<DeliveryReceipt> consume(String consumer,OutboxEnvelope event){if(!store.claimReceipt(consumer,event.eventId()))return ModuleResult.Accepted.of(new DeliveryReceipt(event.eventId(),consumer,true));if("WAITLIST_ASSIGNED".equals(event.eventType())||"WAITLIST_EXPIRED".equals(event.eventType())){var reader=((Number)event.payload().get("readerId")).longValue();var raw=event.payload().get("reservationDate");var date=raw instanceof LocalDate d?d:LocalDate.parse(raw.toString());store.insertNotification(reader,"WAITLIST_ASSIGNED".equals(event.eventType())?"VISIT":"NO_VISIT",event.aggregateId(),date);}return ModuleResult.Accepted.of(new DeliveryReceipt(event.eventId(),consumer,false));}
    private Notification notification(DeliveryStore.NotificationRow row){return new Notification(row.id(),row.kind(),message(row.kind()),row.readAt()!=null,row.bizRef(),row.reservationDate(),row.createdAt());}
    private String message(String kind){return "VISIT".equals(kind)?"请到馆办理取书":"本次无需到馆";}
}
