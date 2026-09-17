package com.library.ai.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.contract.*;
import com.library.application.querying.QueryUseCases;
import com.library.catalog.contract.*;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.AvailabilityQuery;
import com.library.circulation.contract.CopyCommands;
import com.library.circulation.contract.model.BusinessDate;
import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.Visibility;
import com.library.delivery.contract.AuditQuery;
import com.library.delivery.contract.NotificationQuery;
import com.library.identity.contract.AccountCriteria;
import com.library.identity.contract.AccountQuery;
import com.library.review.contract.ReviewQuery;
import com.library.settlement.contract.FineQuery;
import com.library.settlement.contract.OrderQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/** 已批准 L1 工具到真实只读能力接口的唯一映射。 */
@Component
final class ToolExecutor {
    private final AccountQuery accounts; private final TitleQuery titles; private final ReadingContentQuery reading;
    private final CopyCommands copies; private final AvailabilityQuery availability; private final FineQuery fines;
    private final OrderQuery orders; private final ReviewQuery reviews; private final QueryUseCases queries;
    private final NotificationQuery notifications; private final AuditQuery audit; private final ObjectMapper json;
    ToolExecutor(AccountQuery accounts,TitleQuery titles,ReadingContentQuery reading,CopyCommands copies,
                 AvailabilityQuery availability,FineQuery fines,OrderQuery orders,ReviewQuery reviews,
                 QueryUseCases queries,NotificationQuery notifications,AuditQuery audit,ObjectMapper json){
        this.accounts=accounts;this.titles=titles;this.reading=reading;this.copies=copies;this.availability=availability;
        this.fines=fines;this.orders=orders;this.reviews=reviews;this.queries=queries;this.notifications=notifications;this.audit=audit;this.json=json;
    }
    String execute(String name,Map<String,Object> a,ActorContext actor){
        PageCursor page=PageCursor.of(text(a,"cursor"),integer(a,"limit"));
        ModuleResult<?> result=switch(name){
            case "list_accounts" -> accounts.accounts(new AccountCriteria(text(a,"role"),text(a,"status"),page));
            case "search_titles" -> titles.search(new TitleCriteria(text(a,"q"),text(a,"category"),page),Visibility.PUBLIC);
            case "get_title" -> titles.title(TitleId.of(number(a,"titleId")),Visibility.PUBLIC);
            case "get_reading_content" -> reading.readingContent(TitleId.of(number(a,"titleId")),page);
            case "list_copies" -> copies.listCopies(new CopyCommands.CopyCriteria(optionalTitle(a),text(a,"status"),page));
            case "get_reservation_calendar" -> availability.calendar(TitleId.of(number(a,"titleId")),date(a,"from"),date(a,"to"));
            case "list_fines" -> fines.fines(new FineQuery.FineCriteria(actor!=null&&actor.role()==com.library.contract.model.Role.READER?actor.accountId().value():null,text(a,"status"),page));
            case "get_order" -> orders.order(number(a,"orderId"),actor);
            case "list_reviews" -> reviews.reviews(new ReviewQuery.ReviewCriteria(text(a,"status"),page));
            case "get_arrival_dashboard" -> queries.arrivals(actor,date(a,"businessDate"));
            case "get_my_business" -> queries.myBusiness(actor,text(a,"kind"),text(a,"status"),page);
            case "list_notifications" -> notifications.notifications(new NotificationQuery.NotificationCriteria(Boolean.TRUE.equals(a.get("unreadOnly")),page),actor);
            case "list_audit_events" -> audit.audit(new AuditQuery.AuditCriteria(optionalLong(a,"relatedId"),null,text(a,"eventType"),page),actor);
            default -> throw new ToolRegistry.ToolRejected("工具没有只读 handler");
        };
        try{return json.writeValueAsString(result);}catch(Exception e){throw new ToolRegistry.ToolRejected("工具结果编码失败");}
    }
    private String text(Map<String,Object>a,String k){var v=a.get(k);return v==null?null:String.valueOf(v);}
    private Integer integer(Map<String,Object>a,String k){var v=a.get(k);return v instanceof Number n?n.intValue():null;}
    private long number(Map<String,Object>a,String k){var v=a.get(k);if(v instanceof Number n)return n.longValue();if(v!=null)return Long.parseLong(v.toString());throw new ToolRegistry.ToolRejected(k+" 必填");}
    private Long optionalLong(Map<String,Object>a,String k){return a.containsKey(k)?number(a,k):null;}
    private TitleId optionalTitle(Map<String,Object>a){return a.containsKey("titleId")?TitleId.of(number(a,"titleId")):null;}
    private BusinessDate date(Map<String,Object>a,String k){return BusinessDate.of(a.containsKey(k)?LocalDate.parse(a.get(k).toString()):LocalDate.now(com.library.contract.time.BusinessClock.ZONE));}
}
