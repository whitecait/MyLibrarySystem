package com.library.query.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.GuaranteeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * QUERY 我的业务组合查询能力（GET /me/business 的承载；MLS-DOC-MD-0004 §2）。
 * kind ∈ RESERVATION|LOAN|FINE|ORDER（B2 仅 RESERVATION/LOAN/FINE 有事实来源）。
 */
public interface MyBusinessQuery {

    ModuleResult<MyBusinessPage> myBusiness(MyBusinessCriteria criteria, ActorContext actor);

    record MyBusinessCriteria(String kind, String status, String group, PageCursor page) {
        public MyBusinessCriteria(String kind, String status, PageCursor page) {
            this(kind, status, null, page);
        }
    }

    record MyBusinessPage(List<BusinessItem> items, String nextCursor) {
    }

    /** 业务条目；新增的流通详情字段只来自本人事务与关联通知快照。 */
    record BusinessItem(String kind, long id, String status, String title,
                        String businessDate, Long amountCents, String reservationDate,
                        String pickupSlot, Long copyId, GuaranteeType guaranteeType,
                        List<NotificationFact> notifications) {
        public BusinessItem(String kind, long id, String status, String title,
                            String businessDate, Long amountCents, String reservationDate,
                            String pickupSlot, Long copyId, List<NotificationFact> notifications) {
            this(kind, id, status, title, businessDate, amountCents, reservationDate,
                    pickupSlot, copyId, null, notifications);
        }

        public BusinessItem(String kind, long id, String status, String title,
                            String businessDate, Long amountCents) {
            this(kind, id, status, title, businessDate, amountCents, null, null, null, null, List.of());
        }

        public BusinessItem {
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }
    }

    record NotificationFact(long notificationId, String type, boolean read,
                            LocalDate reservationDate, Instant createdAt) {
    }
}
