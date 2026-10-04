package com.library.delivery.internal.adapter.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface DeliveryStore {
    List<NotificationRow> notifications(long readerId,boolean unreadOnly,long afterId,int limit);
    List<NotificationRow> notificationsForTransactions(long readerId,List<Long> transactionIds);
    boolean markRead(long notificationId,long readerId,Instant at);
    List<AuditRow> audits(Long relatedId,Long actorId,String eventType,long afterId,int limit);
    boolean claimReceipt(String consumerId,long eventId);
    void insertNotification(long readerId,String kind,long bizRef,LocalDate reservationDate);
    record NotificationRow(long id,long readerId,String kind,long bizRef,LocalDate reservationDate,Instant readAt,Instant createdAt) {}
    record AuditRow(long id,String eventType,Long actorId,Long relatedId,String outcome,Instant occurredAt) {}
}
