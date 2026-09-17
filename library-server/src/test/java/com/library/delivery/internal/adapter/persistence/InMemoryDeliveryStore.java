package com.library.delivery.internal.adapter.persistence;

import java.time.*;
import java.util.*;

public final class InMemoryDeliveryStore implements DeliveryStore {
    private final Map<Long,NotificationRow> notes=new LinkedHashMap<>();private final Set<String> receipts=new HashSet<>();private final List<AuditRow> audits=new ArrayList<>();private long seq;
    public List<NotificationRow> notifications(long reader,boolean unread,long after,int limit){return notes.values().stream().filter(n->n.readerId()==reader&&n.id()>after).filter(n->!unread||n.readAt()==null).sorted(java.util.Comparator.comparing(NotificationRow::reservationDate).thenComparing(NotificationRow::createdAt)).limit(limit).toList();}
    public boolean markRead(long id,long reader,Instant at){var n=notes.get(id);if(n==null||n.readerId()!=reader)return false;if(n.readAt()==null)notes.put(id,new NotificationRow(n.id(),n.readerId(),n.kind(),n.bizRef(),n.reservationDate(),at,n.createdAt()));return true;}
    public List<AuditRow> audits(Long related,Long actor,String type,long after,int limit){return audits.stream().filter(a->a.id()>after).filter(a->related==null||related.equals(a.relatedId())).filter(a->actor==null||actor.equals(a.actorId())).filter(a->type==null||type.equals(a.eventType())).limit(limit).toList();}
    public boolean claimReceipt(String consumer,long event){return receipts.add(consumer+":"+event);}
    public void insertNotification(long reader,String kind,long ref,LocalDate date){var existing=notes.values().stream().filter(n->n.bizRef()==ref).findFirst();if(existing.isPresent()){if(existing.get().kind().equals(kind))return;notes.remove(existing.get().id());}var id=++seq;notes.put(id,new NotificationRow(id,reader,kind,ref,date,null,Instant.parse("2026-09-17T00:00:00Z").plusSeconds(id)));}
    public int notificationCount(){return notes.size();}
    public void addAudit(AuditRow row){audits.add(row);}
}
