package com.library.delivery.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface NotificationQuery {
    ModuleResult<NotificationPage> notifications(NotificationCriteria query, ActorContext actor);
    /** Returns only this actor's notifications linked to the supplied business transactions. */
    ModuleResult<List<Notification>> notificationsForTransactions(List<Long> transactionIds,
                                                                   ActorContext actor);
    record NotificationCriteria(boolean unreadOnly, PageCursor page) {}
    record Notification(long notificationId,String type,String message,boolean read,Long relatedTxId,LocalDate reservationDate,Instant createdAt) {}
    record NotificationPage(List<Notification> items,String nextCursor) {}
}
