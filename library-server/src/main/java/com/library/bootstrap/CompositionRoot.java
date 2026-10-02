package com.library.bootstrap;

import com.library.catalog.contract.CatalogModule;
import com.library.catalog.contract.ReadingContentQuery;
import com.library.catalog.contract.TitleCommands;
import com.library.catalog.contract.TitleQuery;
import com.library.identity.contract.AccountCommands;
import com.library.identity.contract.AccountQuery;
import com.library.identity.contract.ActorResolver;
import com.library.identity.contract.AuthorizationCheck;
import com.library.identity.contract.IdentityModule;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.ReaderQuery;
import com.library.identity.contract.SessionCommands;
import com.library.identity.contract.SessionToken;
import com.library.review.contract.ReviewModule;
import com.library.review.contract.ReviewRequesting;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition Root（MLS-DOC-MD-0002 §2）：模块 Adapter 由组件扫描装配；
 * 本配置只承担聚合接口的装配与一致性检查。聚合实现标注
 * {@code autowireCandidate = false}：聚合接口不得注入应用用例
 * （MLS-DOC-MD-0004 §2），仅作为「模块 Adapter 完整实现模块接口」的装配证明。
 */
@Configuration
public class CompositionRoot {

    @Bean(autowireCandidate = false)
    IdentityModule identityModule(IdentityRegistration registration,
                                  ActorResolver actorResolver,
                                  AuthorizationCheck authorizationCheck,
                                  ReaderQuery readerQuery,
                                  AccountQuery accountQuery,
                                  AccountCommands accountCommands,
                                  SessionCommands sessionCommands) {
        record Facade(IdentityRegistration r, ActorResolver a, AuthorizationCheck z, ReaderQuery q,
                      AccountQuery aq, AccountCommands ac, SessionCommands sc)
                implements IdentityModule, ActorResolver, AuthorizationCheck, ReaderQuery,
                AccountQuery, AccountCommands, SessionCommands {
            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountRegistration> register(
                    com.library.identity.contract.RegisterReader command) {
                return r.register(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.contract.security.ActorContext> requireActor(
                    SessionToken token) {
                return a.requireActor(token);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.contract.security.AuthorizationDecision> authorize(
                    com.library.contract.security.ActorContext actor,
                    com.library.contract.security.Capability capability,
                    com.library.contract.security.SubjectRef subject) {
                return z.authorize(actor, capability, subject);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.ReaderSnapshot> reader(
                    com.library.identity.contract.model.ReaderId readerId) {
                return q.reader(readerId);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountPage> accounts(
                    com.library.identity.contract.AccountCriteria query) {
                return aq.accounts(query);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountState> accountState(
                    com.library.contract.model.AccountId accountId) {
                return aq.accountState(accountId);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountChange> changeAccount(
                    com.library.identity.contract.ChangeAccount command) {
                return ac.changeAccount(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountChange> applyReviewedChange(
                    com.library.identity.contract.AccountCommands.ReviewedAccountChange command) {
                return ac.applyReviewedChange(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.TemporaryPassword> resetPassword(
                    com.library.identity.contract.ResetPassword command) {
                return ac.resetPassword(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.IssuedSession> issue(
                    String username, String password) {
                return sc.issue(username, password);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.identity.contract.SessionEnded> end(
                    SessionToken token) {
                return sc.end(token);
            }
        }
        return new Facade(registration, actorResolver, authorizationCheck, readerQuery,
                accountQuery, accountCommands, sessionCommands);
    }

    @Bean(autowireCandidate = false)
    CatalogModule catalogModule(TitleQuery titleQuery, TitleCommands titleCommands,
                                ReadingContentQuery readingContentQuery) {
        record Facade(TitleQuery q, TitleCommands c, ReadingContentQuery r)
                implements CatalogModule, TitleQuery, TitleCommands, ReadingContentQuery {
            @Override
            public com.library.contract.ModuleResult<com.library.catalog.contract.TitlePage> search(
                    com.library.catalog.contract.TitleCriteria query,
                    com.library.contract.security.Visibility visibility) {
                return q.search(query, visibility);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.catalog.contract.TitleView> title(
                    com.library.catalog.contract.model.TitleId titleId,
                    com.library.contract.security.Visibility visibility) {
                return q.title(titleId, visibility);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.catalog.contract.TitleChange> maintain(
                    com.library.catalog.contract.MaintainTitle command) {
                return c.maintain(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.catalog.contract.ReadingContent> readingContent(
                    com.library.catalog.contract.model.TitleId titleId,
                    com.library.contract.model.PageCursor page) {
                return r.readingContent(titleId, page);
            }
        }
        return new Facade(titleQuery, titleCommands, readingContentQuery);
    }

    @Bean(autowireCandidate = false)
    ReviewModule reviewModule(ReviewRequesting requesting,
                              com.library.review.contract.ReviewDeciding deciding,
                              com.library.review.contract.ReviewQuery query) {
        record Facade(ReviewRequesting r, com.library.review.contract.ReviewDeciding d,
                      com.library.review.contract.ReviewQuery q) implements ReviewModule {
            @Override
            public com.library.contract.ModuleResult<com.library.review.contract.Review> request(
                    com.library.review.contract.ReviewCommand command) {
                return r.request(command);
            }
            @Override public com.library.contract.ModuleResult<com.library.review.contract.ReviewDeciding.ReviewDecision> decide(com.library.review.contract.ReviewDeciding.DecideReview c, com.library.review.contract.ReviewedAction a){return d.decide(c,a);}
            @Override public com.library.contract.ModuleResult<com.library.review.contract.ReviewQuery.ReviewPage> reviews(com.library.review.contract.ReviewQuery.ReviewCriteria c){return q.reviews(c);}
            @Override public com.library.contract.ModuleResult<com.library.review.contract.ReviewQuery.ReviewDetail> review(long id){return q.review(id);}
        }
        return new Facade(requesting,deciding,query);
    }

    @Bean(autowireCandidate = false)
    com.library.circulation.contract.CirculationModule circulationModule(
            com.library.circulation.contract.AvailabilityQuery availabilityQuery,
            com.library.circulation.contract.ReservationCommands reservationCommands,
            com.library.circulation.contract.LoanCommands loanCommands,
            com.library.circulation.contract.ReturnCommands returnCommands,
            com.library.circulation.contract.CopyCommands copyCommands,
            com.library.circulation.contract.ClosingCommands closingCommands) {
        record Facade(com.library.circulation.contract.AvailabilityQuery av,
                      com.library.circulation.contract.ReservationCommands rsv,
                      com.library.circulation.contract.LoanCommands ln,
                      com.library.circulation.contract.ReturnCommands rt,
                      com.library.circulation.contract.CopyCommands cp,
                      com.library.circulation.contract.ClosingCommands cl)
                implements com.library.circulation.contract.CirculationModule,
                com.library.circulation.contract.AvailabilityQuery,
                com.library.circulation.contract.ReservationCommands,
                com.library.circulation.contract.LoanCommands,
                com.library.circulation.contract.ReturnCommands,
                com.library.circulation.contract.CopyCommands,
                com.library.circulation.contract.ClosingCommands {
            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.AvailabilityQuery.AvailabilitySnapshot> availability(
                    com.library.catalog.contract.model.TitleId titleId,
                    com.library.circulation.contract.model.BusinessDate date) {
                return av.availability(titleId, date);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.AvailabilityQuery.ReservationCalendar> calendar(
                    com.library.catalog.contract.model.TitleId titleId,
                    com.library.circulation.contract.model.BusinessDate from,
                    com.library.circulation.contract.model.BusinessDate to) {
                return av.calendar(titleId, from, to);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.ReservationCommands.Reservation> reserve(
                    com.library.circulation.contract.ReservationCommands.ReservationCommand command) {
                return rsv.reserve(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.Transaction> cancel(
                    com.library.circulation.contract.ReservationCommands.CancelReservation command) {
                return rsv.cancel(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.LoanCommands.LoanBatch> lend(
                    com.library.circulation.contract.LoanCommands.WalkInLoanCommand command) {
                return ln.lend(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.Transaction> claim(
                    com.library.circulation.contract.LoanCommands.ClaimPickup command) {
                return ln.claim(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.Transaction> applyManualAction(
                    com.library.circulation.contract.LoanCommands.ManualAction command) {
                return ln.applyManualAction(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.LoanCommands.Renewal> renew(
                    com.library.circulation.contract.LoanCommands.RenewalCommand command) {
                return ln.renew(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.ReturnCommands.ReturnedCopy> returnCopy(
                    com.library.circulation.contract.ReturnCommands.ReturnCommand command) {
                return rt.returnCopy(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.CopyCommands.CopySnapshot> createCopy(
                    com.library.circulation.contract.CopyCommands.CreateCopy command) {
                return cp.createCopy(command);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.CopyCommands.CopyPage> listCopies(
                    com.library.circulation.contract.CopyCommands.CopyCriteria criteria) {
                return cp.listCopies(criteria);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.circulation.contract.CopyCommands.CopySnapshot> maintainCopy(
                    com.library.circulation.contract.CopyCommands.MaintainCopy command) {
                return cp.maintainCopy(command);
            }
            @Override public com.library.contract.ModuleResult<com.library.circulation.contract.ClosingCommands.ClosingRun> closeBusinessDate(com.library.circulation.contract.ClosingCommands.ClosingCommand c){return cl.closeBusinessDate(c);}
            @Override public com.library.contract.ModuleResult<com.library.circulation.contract.ClosingCommands.ReopenedDate> reopen(com.library.circulation.contract.ClosingCommands.ReopenCommand c){return cl.reopen(c);}
        }
        return new Facade(availabilityQuery, reservationCommands, loanCommands, returnCommands, copyCommands, closingCommands);
    }

    @Bean(autowireCandidate = false)
    com.library.settlement.contract.SettlementModule settlementModule(
            com.library.settlement.contract.DebtQuery debtQuery,
            com.library.settlement.contract.FineCommands fineCommands,
            com.library.settlement.contract.FineQuery fineQuery,
            com.library.settlement.contract.OrderCommands orderCommands,
            com.library.settlement.contract.OrderQuery orderQuery,
            com.library.settlement.contract.RefundCommands refundCommands,
            com.library.settlement.contract.ReconciliationCommands reconciliationCommands) {
        record Facade(com.library.settlement.contract.DebtQuery d,
                      com.library.settlement.contract.FineCommands f,
                      com.library.settlement.contract.FineQuery fq,
                      com.library.settlement.contract.OrderCommands oc,
                      com.library.settlement.contract.OrderQuery oq,
                      com.library.settlement.contract.RefundCommands rf,
                      com.library.settlement.contract.ReconciliationCommands rc)
                implements com.library.settlement.contract.SettlementModule {
            @Override
            public com.library.contract.ModuleResult<com.library.settlement.contract.DebtQuery.DebtSnapshot> debtFor(
                    com.library.identity.contract.model.ReaderId readerId) {
                return d.debtFor(readerId);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.settlement.contract.FineCommands.Fine> createFine(
                    com.library.settlement.contract.FineCommands.FineFromReturn command) {
                return f.createFine(command);
            }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.FineQuery.FinePage> fines(com.library.settlement.contract.FineQuery.FineCriteria q) { return fq.fines(q); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.OrderCommands.Order> createOrder(com.library.settlement.contract.OrderCommands.CreateOrder c) { return oc.createOrder(c); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.OrderCommands.Callback> applyCallback(com.library.settlement.contract.OrderCommands.PaymentCallback c) { return oc.applyCallback(c); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.OrderCommands.Order> closeOrder(com.library.settlement.contract.OrderCommands.CloseOrder c) { return oc.closeOrder(c); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.OrderCommands.Order> order(long id, com.library.contract.security.ActorContext a) { return oq.order(id,a); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.OrderQuery.OrderPage> occupiedOrders(com.library.contract.model.PageCursor p, com.library.contract.security.ActorContext a) { return oq.occupiedOrders(p,a); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.RefundCommands.Refund> refund(com.library.settlement.contract.RefundCommands.ApprovedRefundCommand c) { return rf.refund(c); }
            @Override public com.library.contract.ModuleResult<com.library.settlement.contract.ReconciliationCommands.Reconciliation> reconcile(com.library.settlement.contract.ReconciliationCommands.ReconciliationCommand c) { return rc.reconcile(c); }
        }
        return new Facade(debtQuery, fineCommands, fineQuery, orderCommands, orderQuery,
                refundCommands, reconciliationCommands);
    }

    @Bean(autowireCandidate = false)
    com.library.query.contract.QueryModule queryModule(
            com.library.query.contract.ArrivalQuery arrivalQuery,
            com.library.query.contract.MyBusinessQuery myBusinessQuery) {
        record Facade(com.library.query.contract.ArrivalQuery a,
                      com.library.query.contract.MyBusinessQuery m)
                implements com.library.query.contract.QueryModule,
                com.library.query.contract.ArrivalQuery,
                com.library.query.contract.MyBusinessQuery {
            @Override
            public com.library.contract.ModuleResult<com.library.query.contract.ArrivalQuery.ArrivalDashboard> arrivals(
                    com.library.circulation.contract.model.BusinessDate date,
                    com.library.contract.security.ActorContext actor) {
                return a.arrivals(date, actor);
            }

            @Override
            public com.library.contract.ModuleResult<com.library.query.contract.MyBusinessQuery.MyBusinessPage> myBusiness(
                    com.library.query.contract.MyBusinessQuery.MyBusinessCriteria criteria,
                    com.library.contract.security.ActorContext actor) {
                return m.myBusiness(criteria, actor);
            }
        }
        return new Facade(arrivalQuery, myBusinessQuery);
    }
}
