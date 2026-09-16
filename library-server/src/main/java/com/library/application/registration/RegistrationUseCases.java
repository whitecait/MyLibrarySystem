package com.library.application.registration;

import com.library.contract.ModuleResult;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.identity.contract.AccountChange;
import com.library.identity.contract.AccountCommands;
import com.library.identity.contract.AccountCriteria;
import com.library.identity.contract.AccountPage;
import com.library.identity.contract.AccountQuery;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.AuthorizationCheck;
import com.library.identity.contract.ChangeAccount;
import com.library.identity.contract.IssuedSession;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.ReaderQuery;
import com.library.identity.contract.ReaderSnapshot;
import com.library.identity.contract.RegisterReader;
import com.library.identity.contract.ResetPassword;
import com.library.identity.contract.SessionCommands;
import com.library.identity.contract.SessionEnded;
import com.library.identity.contract.SessionToken;
import com.library.identity.contract.TemporaryPassword;
import com.library.identity.contract.model.ReaderId;
import com.library.review.contract.Review;
import com.library.review.contract.ReviewCommand;
import com.library.review.contract.ReviewRequesting;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 注册与账户维护应用用例（MLS-DOC-MD-0004 §3）：账户与读者资料整体提交。
 * 高风险账户变更（SUSPEND/RESTORE/CHANGE_ROLE）在此发起复核，不直接生效；
 * 授权判断在用例内先行，模块接口只处理业务守卫。
 */
public interface RegistrationUseCases {

    ModuleResult<AccountRegistration> register(RegisterReader command);

    ModuleResult<IssuedSession> createSession(String username, String password);

    ModuleResult<SessionEnded> endCurrentSession(SessionToken token);

    ModuleResult<AccountPage> listAccounts(ActorContext actor, String role, String status, PageCursor page);

    ModuleResult<Review> requestAccountChange(ActorContext actor, long accountId, String action,
                                              Map<String, Object> payload, String reason);

    ModuleResult<TemporaryPassword> resetPassword(ActorContext actor, long accountId, String reason);

    ModuleResult<AccountChange> changeReaderStatus(ActorContext actor, long readerId,
                                                   String status, String reason);
}

@Component
record RegistrationUseCasesImpl(IdentityRegistration identity,
                                SessionCommands sessions,
                                AuthorizationCheck authorization,
                                AccountQuery accountQuery,
                                AccountCommands accountCommands,
                                ReaderQuery readerQuery,
                                ReviewRequesting reviews) implements RegistrationUseCases {

    @Override
    public ModuleResult<AccountRegistration> register(RegisterReader command) {
        return identity.register(command);
    }

    @Override
    public ModuleResult<IssuedSession> createSession(String username, String password) {
        return sessions.issue(username, password);
    }

    @Override
    public ModuleResult<SessionEnded> endCurrentSession(SessionToken token) {
        return sessions.end(token);
    }

    @Override
    public ModuleResult<AccountPage> listAccounts(ActorContext actor, String role, String status,
                                                  PageCursor page) {
        var rejected = authorizeOrReject(actor, Capability.ADMIN, SubjectRef.system());
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return accountQuery.accounts(new AccountCriteria(role, status, page));
    }

    @Override
    public ModuleResult<Review> requestAccountChange(ActorContext actor, long accountId, String action,
                                                     Map<String, Object> payload, String reason) {
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN,
                SubjectRef.account(String.valueOf(accountId)));
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return reviews.request(new ReviewCommand(actor.accountId().value(), "ACCOUNT_CHANGE",
                accountId, action, payload, reason, targetVersionOf(accountId)));
    }

    @Override
    public ModuleResult<TemporaryPassword> resetPassword(ActorContext actor, long accountId, String reason) {
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN_OR_ADMIN,
                SubjectRef.account(String.valueOf(accountId)));
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return accountCommands.resetPassword(new ResetPassword(AccountId.of(accountId), reason));
    }

    @Override
    public ModuleResult<AccountChange> changeReaderStatus(ActorContext actor, long readerId,
                                                          String status, String reason) {
        var rejected = authorizeOrReject(actor, Capability.LIBRARIAN_OR_ADMIN,
                SubjectRef.account(String.valueOf(readerId)));
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return accountCommands.changeAccount(new ChangeAccount(AccountId.of(readerId), "READER_STATUS",
                Map.of("status", status == null ? "" : status), reason));
    }

    private ModuleResult.Rejected<AuthorizationDecision> authorizeOrReject(ActorContext actor,
                                                                           Capability capability,
                                                                           SubjectRef subject) {
        if (authorization.authorize(actor, capability, subject)
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }

    /** 目标账户当前版本快照（发起时固定，批准时重验）。非读者目标回退 0，由复核守卫兜底。 */
    private long targetVersionOf(long accountId) {
        if (readerQuery.reader(ReaderId.of(accountId))
                instanceof ModuleResult.Accepted<ReaderSnapshot> accepted) {
            return accepted.value().version();
        }
        return 0L;
    }
}
