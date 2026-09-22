package com.library.identity.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.AccountId;
import com.library.contract.model.PageCursor;
import com.library.contract.model.Role;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.contract.time.BusinessClock;
import com.library.identity.contract.AccountChange;
import com.library.identity.contract.AccountCommands;
import com.library.identity.contract.AccountCriteria;
import com.library.identity.contract.AccountPage;
import com.library.identity.contract.AccountQuery;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.AccountSummary;
import com.library.identity.contract.ActorResolver;
import com.library.identity.contract.AuthorizationCheck;
import com.library.identity.contract.ChangeAccount;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.IssuedSession;
import com.library.identity.contract.ReaderQuery;
import com.library.identity.contract.ReaderSnapshot;
import com.library.identity.contract.RegisterReader;
import com.library.identity.contract.ResetPassword;
import com.library.identity.contract.SessionCommands;
import com.library.identity.contract.SessionEnded;
import com.library.identity.contract.SessionToken;
import com.library.identity.contract.TemporaryPassword;
import com.library.identity.contract.model.AccountStatus;
import com.library.identity.contract.model.ReaderId;
import com.library.identity.internal.adapter.persistence.AccountStore;
import com.library.identity.internal.adapter.persistence.SessionStore;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * IDENTITY 模块实现：注册、会话身份、授权判断、账户与读者状态办理。
 * 规则与持久化细节隐藏在模块内；调用方只经能力子接口观察 Accepted/Rejected。
 */
@Component
public class IdentityModuleImpl implements IdentityRegistration, ActorResolver, AuthorizationCheck,
        ReaderQuery, AccountQuery, AccountCommands, SessionCommands {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> READER_API_STATUS = Set.of("ACTIVE", "SUSPENDED");

    private final AccountStore accounts;
    private final SessionStore sessions;
    private final BusinessClock clock;

    public IdentityModuleImpl(AccountStore accounts, SessionStore sessions, BusinessClock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    // ---- 注册（B0 探针能力） ------------------------------------------------

    @Override
    public ModuleResult<AccountRegistration> register(RegisterReader command) {
        var fieldRejection = validateRegistration(command);
        if (fieldRejection != null) {
            return fieldRejection;
        }
        if (accounts.usernameExists(command.username())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_002.code(), "用户名已存在");
        }
        AccountStore.StoredAccount stored;
        try {
            stored = accounts.insert(new AccountStore.NewAccount(
                    command.username(), PasswordHasher.hash(command.password()), command.displayName()));
        } catch (AccountStore.DuplicateUsername concurrent) {
            // 并发注册竞争（IT-ID-001）：唯一索引守卫转换为稳定拒绝，不产生异常逃逸
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_002.code(), "用户名已存在");
        }
        return ModuleResult.Accepted.of(new AccountRegistration(
                AccountId.of(stored.id()), Role.READER, AccountStatus.valueOf(stored.status())));
    }

    private ModuleResult.Rejected<AccountRegistration> validateRegistration(RegisterReader command) {
        if (command.username() == null || command.username().isEmpty() || command.username().length() > 64) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_003.code(), "username 必须为 1~64 字符");
        }
        if (command.password() == null || command.password().length() < 8 || command.password().length() > 128) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_003.code(), "password 必须为 8~128 字符");
        }
        if (command.displayName() == null || command.displayName().isEmpty() || command.displayName().length() > 100) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_003.code(), "displayName 必须为 1~100 字符");
        }
        return null;
    }

    // ---- 会话身份 -----------------------------------------------------------

    @Override
    public ModuleResult<IssuedSession> issue(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_004.code(), "凭据缺失");
        }
        var account = accounts.byUsername(username)
                .orElse(null);
        if (account == null || !PasswordHasher.matches(password, account.passwordHash())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_004.code(), "用户名或密码不正确");
        }
        if (!"ACTIVE".equals(account.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "账户不可用（已停用或禁用）");
        }
        var token = newToken();
        var expiresAt = clock.now().plus(SESSION_TTL);
        sessions.insert(account.id(), JdbcTokens.sha256(token), expiresAt);
        var actor = new ActorContext(AccountId.of(account.id()), Role.valueOf(account.role()), clock.now());
        return ModuleResult.Accepted.of(new IssuedSession(new SessionToken(token), expiresAt, actor,
                account.displayName()));
    }

    @Override
    public ModuleResult<SessionEnded> end(SessionToken token) {
        var hash = JdbcTokens.sha256(token.value());
        var session = sessions.byTokenHash(hash).orElse(null);
        if (session != null) {
            sessions.end(session.id());
        }
        // 幂等：无有效会话也返回 ended=true（作用域含幂等键，重复提交返回首次结果）
        return ModuleResult.Accepted.of(new SessionEnded(true));
    }

    @Override
    public ModuleResult<ActorContext> requireActor(SessionToken token) {
        if (token == null || token.value().isBlank()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：缺少会话令牌");
        }
        var session = sessions.byTokenHash(JdbcTokens.sha256(token.value())).orElse(null);
        if (session == null || session.endedAt() != null || session.expiresAt().isBefore(clock.now())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：会话无效或已过期");
        }
        return accounts.byId(session.accountId())
                .<ModuleResult<ActorContext>>map(account -> ModuleResult.Accepted.of(new ActorContext(
                        AccountId.of(account.id()), Role.valueOf(account.role()), session.createdAt())))
                .orElseGet(() -> ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：账户不存在"));
    }

    // ---- 授权判断 -----------------------------------------------------------

    @Override
    public ModuleResult<AuthorizationDecision> authorize(ActorContext actor, Capability capability,
                                                         SubjectRef subject) {
        var role = actor == null ? null : actor.role();
        var allowed = switch (capability.value()) {
            case "PUBLIC", "PUBLIC_OR_AUTHENTICATED" -> true;
            case "AUTHENTICATED", "AUTHENTICATED_SELF" -> actor != null;
            case "READER" -> role == Role.READER;
            case "ADMIN" -> role == Role.ADMIN;
            case "LIBRARIAN" -> role == Role.LIBRARIAN;
            case "LIBRARIAN_OR_ADMIN" -> role == Role.LIBRARIAN || role == Role.ADMIN;
            case "LIBRARIAN_THEN_ADMIN_REVIEW" -> role == Role.LIBRARIAN;
            case "SYSTEM" -> role == Role.SYSTEM;
            case "SYSTEM_OR_ADMIN" -> role == Role.SYSTEM || role == Role.ADMIN;
            // 2026-09-22 lingzz 裁定（M6 恢复计划 §8 第 2 项）：模拟支付调度能力限定 ADMIN
            case "CONTROLLED_TEST_OPERATOR" -> role == Role.ADMIN;
            case "READER_SELF" -> isSelf(actor, subject);
            case "READER_SELF_OR_LIBRARIAN" -> role == Role.LIBRARIAN || isSelf(actor, subject);
            case "READER_SELF_OR_LIBRARIAN_OR_SYSTEM" ->
                    role == Role.LIBRARIAN || role == Role.SYSTEM || isSelf(actor, subject);
            default -> false; // SIMULATOR_CREDENTIAL/SESSION_OWNER 随批次登记
        };
        if (!allowed) {
            if (actor == null) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：需要登录");
            }
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(), "无权限执行该操作");
        }
        return ModuleResult.Accepted.of(AuthorizationDecision.allow());
    }

    private boolean isSelf(ActorContext actor, SubjectRef subject) {
        return actor != null && SubjectRef.ACCOUNT.equals(subject.type())
                && actor.accountId().asString().equals(subject.id())
                && actor.role() == Role.READER;
    }

    // ---- 读者与账户办理 ------------------------------------------------------

    @Override
    public ModuleResult<ReaderSnapshot> reader(ReaderId readerId) {
        var account = accounts.byId(readerId.value()).orElse(null);
        if (account == null || !Role.READER.name().equals(account.role())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_007.code(), "读者不存在或不可见");
        }
        return ModuleResult.Accepted.of(new ReaderSnapshot(ReaderId.of(account.id()), account.status(),
                account.version()));
    }

    @Override
    public ModuleResult<AccountPage> accounts(AccountCriteria query) {
        var cursor = query.page() == null ? PageCursor.first() : query.page();
        var afterId = cursor.cursor() == null ? 0 : Long.parseLong(cursor.cursor());
        var rows = accounts.list(
                upper(query.role()), upper(query.status()), afterId, cursor.limit() + 1);
        var hasNext = rows.size() > cursor.limit();
        var items = rows.stream().limit(cursor.limit())
                .map(row -> new AccountSummary(AccountId.of(row.id()), row.username(), row.displayName(),
                        row.role(), row.status(), row.version()))
                .toList();
        var nextCursor = hasNext && !items.isEmpty()
                ? String.valueOf(items.get(items.size() - 1).accountId().value()) : null;
        return ModuleResult.Accepted.of(new AccountPage(items, nextCursor));
    }

    @Override
    public ModuleResult<AccountChange> changeAccount(ChangeAccount command) {
        var target = accounts.byId(command.target().value()).orElse(null);
        if (target == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_007.code(), "目标账户不存在或不可见");
        }
        var action = command.action() == null ? "" : command.action();
        switch (action) {
            case "READER_STATUS" -> {
                if (!Role.READER.name().equals(target.role())) {
                    return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_006.code(), "目标账户不是读者");
                }
                var nextStatus = upper(text(command.payload().get("status")));
                if (!READER_API_STATUS.contains(nextStatus)) {
                    return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_006.code(),
                            "读者状态仅允许 ACTIVE/SUSPENDED");
                }
                var version = accounts.updateReaderStatus(target.id(), nextStatus);
                return ModuleResult.Accepted.of(new AccountChange(AccountId.of(target.id()),
                        nextStatus, target.role(), version));
            }
            case "SUSPEND", "RESTORE", "CHANGE_ROLE" -> {
                // 高风险动作只由复核批准事务执行（MLS-DOC-MD-0004 §2.1；批准路径 B3 登记）
                return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_006.code(),
                        "高风险账户变更须经管理员复核批准");
            }
            default -> {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_006.code(), "未知账户维护动作");
            }
        }
    }

    @Override
    public ModuleResult<TemporaryPassword> resetPassword(ResetPassword command) {
        var target = accounts.byId(command.target().value()).orElse(null);
        if (target == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_007.code(), "目标账户不存在或不可见");
        }
        if ("DISABLED".equals(target.status())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_006.code(), "账户已禁用，不能重置密码");
        }
        var temporary = newToken().substring(0, 16);
        accounts.updatePasswordHash(target.id(), PasswordHasher.hash(temporary));
        return ModuleResult.Accepted.of(new TemporaryPassword(temporary, true));
    }

    // ---- 内部工具 ------------------------------------------------------------

    private static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** 令牌摘要工具（HexFormat 封装；供会话存储两侧共用）。 */
    static final class JdbcTokens {
        private JdbcTokens() {
        }

        static String sha256(String raw) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new com.library.contract.InfrastructureFailure("SHA-256 不可用", e);
            }
        }
    }
}
