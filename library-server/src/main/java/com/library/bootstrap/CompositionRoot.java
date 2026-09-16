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
            public com.library.contract.ModuleResult<com.library.identity.contract.AccountChange> changeAccount(
                    com.library.identity.contract.ChangeAccount command) {
                return ac.changeAccount(command);
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
    ReviewModule reviewModule(ReviewRequesting requesting) {
        record Facade(ReviewRequesting r) implements ReviewModule, ReviewRequesting {
            @Override
            public com.library.contract.ModuleResult<com.library.review.contract.Review> request(
                    com.library.review.contract.ReviewCommand command) {
                return r.request(command);
            }
        }
        return new Facade(requesting);
    }
}
