package com.library.application.querying;

import com.library.contract.InfrastructureFailure;
import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.circulation.contract.model.BusinessDate;
import com.library.circulation.contract.model.TxId;
import com.library.delivery.contract.NotificationQuery;
import com.library.delivery.contract.model.NotificationId;
import com.library.identity.contract.AuthorizationCheck;
import com.library.query.contract.ArrivalQuery;
import com.library.query.contract.MyBusinessQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryUseCasesTest {

    private static final ActorContext ACTOR = new ActorContext(AccountId.of(42), Role.READER,
            Instant.parse("2026-10-04T12:00:00Z"));

    @Test
    void readerAuthorizationRejectionStopsBusinessAndNotificationReads() {
        var businessReads = new AtomicInteger();
        var notificationReads = new AtomicInteger();
        var useCases = useCases((actor, capability, subject) -> ModuleResult.Rejected.of("MLS-ID-005"),
                (criteria, actor) -> {
                    businessReads.incrementAndGet();
                    return ModuleResult.Accepted.of(emptyPage());
                }, notificationQuery((ids, actor) -> {
                    notificationReads.incrementAndGet();
                    return ModuleResult.Accepted.of(List.of());
                }));

        var result = useCases.myBusiness(ACTOR, null, null, PageCursor.first());

        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo("MLS-ID-005");
        assertThat(businessReads).hasValue(0);
        assertThat(notificationReads).hasValue(0);
    }

    @Test
    void rejectedBusinessPageStopsNotificationRead() {
        var notificationReads = new AtomicInteger();
        var useCases = useCases(authorized(),
                (criteria, actor) -> ModuleResult.Rejected.of("MLS-QUERY-001", "bad criteria"),
                notificationQuery((ids, actor) -> {
                    notificationReads.incrementAndGet();
                    return ModuleResult.Accepted.of(List.of());
                }));

        var result = useCases.myBusiness(ACTOR, null, null, PageCursor.first());

        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo("MLS-QUERY-001");
        assertThat(notificationReads).hasValue(0);
    }

    @Test
    void notificationLookupUsesCurrentActorAndTransactionRowsOnly() {
        var loanId = 99007L;
        var reservationId = 99008L;
        var date = LocalDate.parse("2026-10-05");
        var source = new MyBusinessQuery.MyBusinessPage(List.of(
                new MyBusinessQuery.BusinessItem("FINE", loanId, "DUE", null, null, 725L),
                new MyBusinessQuery.BusinessItem("LOAN", loanId, "RETURN_ABNORMAL", "same id loan",
                        "2026-10-04", null, BusinessDate.of(date), "PM", null, null, List.of()),
                new MyBusinessQuery.BusinessItem("RESERVATION", reservationId, "CONFIRMED", "reserved",
                        "2026-10-05", null, BusinessDate.of(date), "AM", null, null, List.of())), "99008:RESERVATION");
        var requestedIds = new ArrayList<TxId>();
        var requestedActor = new ActorContext[1];
        var notification = new NotificationQuery.Notification(501, "VISIT", "please visit", false,
                loanId, date, Instant.parse("2026-10-04T13:00:00Z"));
        var useCases = useCases(authorized(), (criteria, actor) -> ModuleResult.Accepted.of(source),
                notificationQuery((ids, actor) -> {
                    requestedIds.addAll(ids);
                    requestedActor[0] = actor;
                    return ModuleResult.Accepted.of(List.of(notification));
                }));

        var result = accepted(useCases.myBusiness(ACTOR, null, null, PageCursor.first()));

        assertThat(requestedIds).containsExactly(TxId.of(loanId), TxId.of(reservationId));
        assertThat(requestedActor[0]).isSameAs(ACTOR);
        assertThat(result.nextCursor()).isEqualTo(source.nextCursor());
        assertThat(result.items().get(0).notifications()).isEmpty();
        var loanNotes = result.items().get(1).notifications();
        assertThat(loanNotes).singleElement().satisfies(note -> {
            assertThat(note.notificationId()).isEqualTo(NotificationId.of(501));
            assertThat(note.type()).isEqualTo("VISIT");
            assertThat(note.read()).isFalse();
            assertThat(note.reservationDate()).isEqualTo(BusinessDate.of(date));
        });
        assertThat(result.items().get(2).notifications()).isEmpty();
    }

    @Test
    void pageWithoutReservationOrLoanDoesNotReadNotifications() {
        var notificationReads = new AtomicInteger();
        var page = new MyBusinessQuery.MyBusinessPage(List.of(
                new MyBusinessQuery.BusinessItem("FINE", 99007, "DUE", null, null, 725L)), null);
        var useCases = useCases(authorized(), (criteria, actor) -> ModuleResult.Accepted.of(page),
                notificationQuery((ids, actor) -> {
                    notificationReads.incrementAndGet();
                    return ModuleResult.Accepted.of(List.of());
                }));

        var result = accepted(useCases.myBusiness(ACTOR, null, null, PageCursor.first()));

        assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.notifications()).isEmpty());
        assertThat(notificationReads).hasValue(0);
    }

    @Test
    void notificationRejectionIsReturnedToCaller() {
        var useCases = useCases(authorized(), (criteria, actor) -> ModuleResult.Accepted.of(pageWithLoan(77)),
                notificationQuery((ids, actor) -> ModuleResult.Rejected.of("MLS-DEL-001", "notification unavailable")));

        var result = useCases.myBusiness(ACTOR, null, null, PageCursor.first());

        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>) result).code()).isEqualTo("MLS-DEL-001");
    }

    @Test
    void notificationInfrastructureFailureIsNotConvertedToAnEmptyFact() {
        var failure = new InfrastructureFailure("delivery database unavailable");
        var useCases = useCases(authorized(), (criteria, actor) -> ModuleResult.Accepted.of(pageWithLoan(77)),
                notificationQuery((ids, actor) -> {
                    throw failure;
                }));

        assertThatThrownBy(() -> useCases.myBusiness(ACTOR, null, null, PageCursor.first()))
                .isSameAs(failure);
    }

    private static AuthorizationCheck authorized() {
        return (actor, capability, subject) -> ModuleResult.Accepted.of(AuthorizationDecision.allow());
    }

    private static QueryUseCasesImpl useCases(AuthorizationCheck authorization,
                                              MyBusinessQuery business,
                                              NotificationQuery notifications) {
        ArrivalQuery arrivals = (date, actor) -> ModuleResult.Accepted.of(
                new ArrivalQuery.ArrivalDashboard(date, 0, 0, 0, 0));
        return new QueryUseCasesImpl(arrivals, business, authorization, notifications);
    }

    private static NotificationQuery notificationQuery(NotificationLookup lookup) {
        return new NotificationQuery() {
            @Override
            public ModuleResult<NotificationPage> notifications(NotificationCriteria query, ActorContext actor) {
                throw new UnsupportedOperationException("not used by my-business query");
            }

            @Override
            public ModuleResult<List<Notification>> notificationsForTransactions(List<TxId> transactionIds,
                                                                                   ActorContext actor) {
                return lookup.find(transactionIds, actor);
            }
        };
    }

    private static MyBusinessQuery.MyBusinessPage emptyPage() {
        return new MyBusinessQuery.MyBusinessPage(List.of(), null);
    }

    private static MyBusinessQuery.MyBusinessPage pageWithLoan(long id) {
        return new MyBusinessQuery.MyBusinessPage(List.of(
                new MyBusinessQuery.BusinessItem("LOAN", id, "LENT", "title", null, null)), null);
    }

    private static MyBusinessQuery.MyBusinessPage accepted(ModuleResult<MyBusinessQuery.MyBusinessPage> result) {
        return ((ModuleResult.Accepted<MyBusinessQuery.MyBusinessPage>) result).value();
    }

    @FunctionalInterface
    private interface NotificationLookup {
        ModuleResult<List<NotificationQuery.Notification>> find(List<TxId> transactionIds, ActorContext actor);
    }
}
