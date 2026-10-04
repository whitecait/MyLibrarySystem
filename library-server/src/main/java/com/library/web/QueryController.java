package com.library.web;

import com.library.contract.model.PageCursor;
import com.library.circulation.contract.model.BusinessDate;
import com.library.application.querying.QueryUseCases;
import com.library.web.Envelope;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAPI Query 操作：getArrivalDashboard（LIBRARIAN）、getMyBusiness（READER）。
 */
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private final QueryUseCases queries;
    private final ApiResponses responses;

    public QueryController(QueryUseCases queries, ApiResponses responses) {
        this.queries = queries;
        this.responses = responses;
    }

    @GetMapping("/dashboard/arrivals")
    public ResponseEntity<Envelope> arrivals(HttpServletRequest request,
                                             @RequestParam String businessDate) {
        BusinessDate date;
        try {
            date = BusinessDate.parse(businessDate);
        } catch (Exception e) {
            return responses.forResult(
                    com.library.contract.ModuleResult.Rejected.of(
                            com.library.contract.error.StableErrorCode.MLS_QUERY_001.code(), "日期无效"),
                    com.library.contract.error.StableErrorCode.MLS_QUERY_001.code(),
                    null, "/api/v1/dashboard/arrivals", null, null);
        }
        var result = queries.arrivals(Actors.actor(request), date);
        return responses.forResult(result, SuccessCodes.of("getArrivalDashboard"),
                Actors.actorId(request), "/api/v1/dashboard/arrivals",
                dashboard -> Map.of(
                        "businessDate", dashboard.businessDate().asString(),
                        "expected", dashboard.expected(),
                        "arrived", dashboard.arrived(),
                        "pending", dashboard.pending(),
                        "exceptions", dashboard.exceptions()));
    }

    @GetMapping("/me/business")
    public ResponseEntity<Envelope> myBusiness(HttpServletRequest request,
                                               @RequestParam(required = false) String kind,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String group,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        var result = queries.myBusiness(Actors.actor(request), kind, status, group,
                PageCursor.of(cursor, limit));
        return responses.forResult(result, SuccessCodes.of("getMyBusiness"),
                Actors.actorId(request), "/api/v1/me/business",
                page -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("items", page.items().stream().map(item -> {
                        var entry = new LinkedHashMap<String, Object>();
                        entry.put("kind", item.kind());
                        entry.put("id", String.valueOf(item.id()));
                        entry.put("status", item.status());
                        entry.put("title", item.title());
                        entry.put("businessDate", item.businessDate());
                        entry.put("amountCents", item.amountCents());
                        entry.put("reservationDate", item.reservationDate());
                        entry.put("pickupSlot", item.pickupSlot());
                        entry.put("copyId", item.copyId() == null ? null : String.valueOf(item.copyId()));
                        entry.put("guaranteeType", item.guaranteeType() == null
                                ? null : item.guaranteeType().name());
                        entry.put("notifications", item.notifications().stream().map(notification -> {
                            var fact = new LinkedHashMap<String, Object>();
                            fact.put("notificationId", String.valueOf(notification.notificationId()));
                            fact.put("type", notification.type());
                            fact.put("read", notification.read());
                            fact.put("reservationDate", notification.reservationDate() == null
                                    ? null : notification.reservationDate().toString());
                            fact.put("createdAt", notification.createdAt() == null
                                    ? null : notification.createdAt().toString());
                            return fact;
                        }).toList());
                        return entry;
                    }).toList());
                    data.put("nextCursor", page.nextCursor());
                    return data;
                });
    }
}
