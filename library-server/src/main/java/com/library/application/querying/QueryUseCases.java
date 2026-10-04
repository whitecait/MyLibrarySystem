package com.library.application.querying;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.TxId;
import com.library.delivery.contract.NotificationQuery;
import com.library.delivery.contract.model.NotificationId;
import com.library.identity.contract.AuthorizationCheck;
import com.library.query.contract.ArrivalQuery;
import com.library.query.contract.MyBusinessQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 查询应用用例（MLS-DOC-MD-0004 §3）：门户、我的业务、首页统计。
 * 只读，不以缓存结果判定提交；到馆统计要求 LIBRARIAN，我的业务限定本人（READER）。
 */
public interface QueryUseCases {

    ModuleResult<ArrivalQuery.ArrivalDashboard> arrivals(ActorContext actor, BusinessDate date);

    ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                            String status, PageCursor page);

    ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                            String status, String group, PageCursor page);
}

@Component
record QueryUseCasesImpl(ArrivalQuery arrivalsQuery,
                         MyBusinessQuery myBusinessQuery,
                         AuthorizationCheck authorization,
                         NotificationQuery notificationQuery) implements QueryUseCases {

    @Override
    public ModuleResult<ArrivalQuery.ArrivalDashboard> arrivals(ActorContext actor, BusinessDate date) {
        if (authorization.authorize(actor, Capability.LIBRARIAN, SubjectRef.system())
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return arrivalsQuery.arrivals(date, actor);
    }

    @Override
    public ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                                   String status, PageCursor page) {
        return myBusiness(actor, kind, status, null, page);
    }

    @Override
    public ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                                   String status, String group,
                                                                   PageCursor page) {
        if (authorization.authorize(actor, Capability.READER,
                SubjectRef.account(actor.accountId().asString()))
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var businessResult = myBusinessQuery.myBusiness(
                new MyBusinessQuery.MyBusinessCriteria(kind, status, group, page), actor);
        if (businessResult instanceof ModuleResult.Rejected<MyBusinessQuery.MyBusinessPage> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var businessPage = ((ModuleResult.Accepted<MyBusinessQuery.MyBusinessPage>) businessResult).value();
        var transactionIds = businessPage.items().stream()
                .filter(item -> isTransactionKind(item.kind()))
                .map(item -> TxId.of(item.id()))
                .distinct()
                .toList();
        if (transactionIds.isEmpty()) {
            return ModuleResult.Accepted.of(businessPage);
        }

        var notificationResult = notificationQuery.notificationsForTransactions(transactionIds, actor);
        if (notificationResult instanceof ModuleResult.Rejected<List<NotificationQuery.Notification>> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        var notificationsByTransaction = new HashMap<Long, List<MyBusinessQuery.NotificationFact>>();
        for (var notification : ((ModuleResult.Accepted<List<NotificationQuery.Notification>>) notificationResult).value()) {
            if (notification.relatedTxId() == null) continue;
            notificationsByTransaction.computeIfAbsent(notification.relatedTxId(), ignored -> new ArrayList<>())
                    .add(new MyBusinessQuery.NotificationFact(
                            NotificationId.of(notification.notificationId()), notification.type(), notification.read(),
                            notification.reservationDate() == null ? null : BusinessDate.of(notification.reservationDate()),
                            notification.createdAt()));
        }
        var items = businessPage.items().stream().map(item -> new MyBusinessQuery.BusinessItem(
                item.kind(), item.id(), item.status(), item.title(), item.businessDate(), item.amountCents(),
                item.reservationDate(), item.pickupSlot(), item.copyId(), item.guaranteeType(),
                isTransactionKind(item.kind())
                        ? notificationsByTransaction.getOrDefault(item.id(), List.of()) : List.of())).toList();
        return ModuleResult.Accepted.of(new MyBusinessQuery.MyBusinessPage(items, businessPage.nextCursor()));
    }

    private static boolean isTransactionKind(String kind) {
        return "RESERVATION".equals(kind) || "LOAN".equals(kind);
    }
}
