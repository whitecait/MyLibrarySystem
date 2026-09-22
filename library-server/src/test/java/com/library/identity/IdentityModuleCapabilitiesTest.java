package com.library.identity;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.contract.time.SystemBusinessClock;
import com.library.identity.contract.AccountChange;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.ActorResolver;
import com.library.identity.contract.IssuedSession;
import com.library.identity.contract.ReaderSnapshot;
import com.library.identity.contract.RegisterReader;
import com.library.identity.contract.SessionToken;
import com.library.identity.contract.model.ReaderId;
import com.library.identity.internal.IdentityModuleImpl;
import com.library.identity.internal.PasswordHasher;
import com.library.identity.internal.adapter.persistence.InMemoryAccountStore;
import com.library.identity.internal.adapter.persistence.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UT-ID-001~006（MLS-DOC-MD-0005 §3）：注册、角色、授权、账户/读者状态，
 * 从模块接口观察返回值；数据库并发语义不在本层证明（IT-ID-001/002）。
 */
class IdentityModuleCapabilitiesTest {

    private final InMemoryAccountStore accounts = new InMemoryAccountStore();
    private final InMemorySessionStore sessions = new InMemorySessionStore();
    private final IdentityModuleImpl module =
            new IdentityModuleImpl(accounts, sessions, new SystemBusinessClock());

    private ActorContext loginAs(String username, String password) {
        var issued = ((ModuleResult.Accepted<IssuedSession>) module.issue(username, password)).value();
        return issued.actor();
    }

    // UT-ID-001 注册：一账户一角色（READER/ACTIVE）、字段守卫、重名守卫
    @Test
    void utId001_registrationRules() {
        var accepted = module.register(new RegisterReader("alice", "password123", "Alice"));
        assertThat(accepted).isInstanceOf(ModuleResult.Accepted.class);
        var registration = ((ModuleResult.Accepted<AccountRegistration>) accepted).value();
        assertThat(registration.role().name()).isEqualTo("READER");
        assertThat(registration.status().name()).isEqualTo("ACTIVE");
        assertThat(PasswordHasher.matches("password123",
                accounts.byUsername("alice").orElseThrow().passwordHash())).isTrue();

        assertThat(codeOf(module.register(new RegisterReader("alice", "password456", "Alice II"))))
                .isEqualTo("MLS-ID-002");
        assertThat(codeOf(module.register(new RegisterReader("", "password123", "X")))).isEqualTo("MLS-ID-003");
        assertThat(codeOf(module.register(new RegisterReader("bob", "short", "X")))).isEqualTo("MLS-ID-003");
        assertThat(codeOf(module.register(new RegisterReader("bob", "password123", null))))
                .isEqualTo("MLS-ID-003");
    }

    // UT-ID-002 登录：凭据失败与账户不可用
    @Test
    void utId002_loginCredentialAndAvailabilityGuards() {
        module.register(new RegisterReader("carol", "password123", "Carol"));
        var accepted = module.issue("carol", "password123");
        assertThat(accepted).isInstanceOf(ModuleResult.Accepted.class);

        assertThat(codeOf(module.issue("carol", "wrong-password"))).isEqualTo("MLS-ID-004");
        assertThat(codeOf(module.issue("nobody", "password123"))).isEqualTo("MLS-ID-004");
        accounts.updateReaderStatus(
                accounts.byUsername("carol").orElseThrow().id(), "SUSPENDED");
        assertThat(codeOf(module.issue("carol", "password123"))).isEqualTo("MLS-ID-005");
    }

    // UT-ID-003 会话解析与吊销：无效/过期/已吊销一律 MLS-ID-001
    @Test
    void utId003_sessionResolutionAndRevocation() {
        module.register(new RegisterReader("dave", "password123", "Dave"));
        var issued = ((ModuleResult.Accepted<IssuedSession>) module.issue("dave", "password123")).value();

        assertThat(module.requireActor(issued.token())).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(codeOf(module.requireActor(new SessionToken("bogus-token")))).isEqualTo("MLS-ID-001");
        assertThat(module.end(issued.token())).isInstanceOf(ModuleResult.Accepted.class);
        assertThat(codeOf(module.requireActor(issued.token()))).isEqualTo("MLS-ID-001");
    }

    // UT-ID-004 授权矩阵：角色、本人主体、匿名与角色不足
    @Test
    void utId004_authorizationMatrix() {
        module.register(new RegisterReader("eve", "password123", "Eve"));
        var reader = loginAs("eve", "password123");

        assertThat(decision(module.authorize(null, Capability.PUBLIC, SubjectRef.system()))).isTrue();
        assertThat(decision(module.authorize(null, Capability.AUTHENTICATED, SubjectRef.system()))).isFalse();
        assertThat(codeOf(module.authorize(null, Capability.AUTHENTICATED, SubjectRef.system())))
                .isEqualTo("MLS-ID-001");
        assertThat(decision(module.authorize(reader, Capability.READER_SELF,
                SubjectRef.account(reader.accountId().asString())))).isTrue();
        assertThat(decision(module.authorize(reader, Capability.READER_SELF,
                SubjectRef.account("9999")))).isFalse();
        assertThat(codeOf(module.authorize(reader, Capability.ADMIN, SubjectRef.system())))
                .isEqualTo("MLS-ID-005");
    }

    // DEF-M6-008 裁定回归（2026-09-22 lingzz，M6 恢复计划 §8 第 2 项）：
    // CONTROLLED_TEST_OPERATOR 映射 ADMIN；其余角色无权、匿名未认证。
    @Test
    void defM6008_controlledTestOperatorMapsToAdminOnly() {
        var admin = new ActorContext(com.library.contract.model.AccountId.of(1),
                com.library.contract.model.Role.ADMIN, Instant.now());
        assertThat(decision(module.authorize(admin, Capability.CONTROLLED_TEST_OPERATOR,
                SubjectRef.system()))).isTrue();

        for (var role : java.util.List.of(com.library.contract.model.Role.READER,
                com.library.contract.model.Role.LIBRARIAN, com.library.contract.model.Role.SYSTEM)) {
            var other = new ActorContext(com.library.contract.model.AccountId.of(2), role, Instant.now());
            assertThat(decision(module.authorize(other, Capability.CONTROLLED_TEST_OPERATOR, SubjectRef.system())))
                    .as("role %s 不得持有 CONTROLLED_TEST_OPERATOR", role).isFalse();
            assertThat(codeOf(module.authorize(other, Capability.CONTROLLED_TEST_OPERATOR, SubjectRef.system())))
                    .isEqualTo("MLS-ID-005");
        }
        assertThat(codeOf(module.authorize(null, Capability.CONTROLLED_TEST_OPERATOR, SubjectRef.system())))
                .isEqualTo("MLS-ID-001");
        // 裁定范围外：SIMULATOR_CREDENTIAL/SESSION_OWNER 仍无角色映射（default 拒绝）
        assertThat(decision(module.authorize(admin, Capability.SIMULATOR_CREDENTIAL,
                SubjectRef.system()))).isFalse();
        assertThat(decision(module.authorize(admin, Capability.SESSION_OWNER, SubjectRef.system()))).isFalse();
    }

    // UT-ID-005 读者状态办理：馆员直办 ACTIVE/SUSPENDED、非读者与非法状态冲突
    @Test
    void utId005_readerStatusHandling() {
        module.register(new RegisterReader("frank", "password123", "Frank"));
        var readerId = accounts.byUsername("frank").orElseThrow().id();

        var suspended = module.changeAccount(new com.library.identity.contract.ChangeAccount(
                com.library.contract.model.AccountId.of(readerId), "READER_STATUS",
                java.util.Map.of("status", "SUSPENDED"), "违规"));
        assertThat(suspended).isInstanceOf(ModuleResult.Accepted.class);
        var change = ((ModuleResult.Accepted<AccountChange>) suspended).value();
        assertThat(change.status()).isEqualTo("SUSPENDED");
        assertThat(change.version()).isEqualTo(1);

        assertThat(codeOf(module.changeAccount(new com.library.identity.contract.ChangeAccount(
                com.library.contract.model.AccountId.of(readerId), "READER_STATUS",
                java.util.Map.of("status", "DISABLED"), "越权状态")))).isEqualTo("MLS-ID-006");
        assertThat(codeOf(module.changeAccount(new com.library.identity.contract.ChangeAccount(
                com.library.contract.model.AccountId.of(999999), "READER_STATUS",
                java.util.Map.of("status", "ACTIVE"), "不存在")))).isEqualTo("MLS-ID-007");

        var reader = module.reader(ReaderId.of(readerId));
        assertThat(((ModuleResult.Accepted<ReaderSnapshot>) reader).value().status()).isEqualTo("SUSPENDED");
        assertThat(codeOf(module.reader(ReaderId.of(999999)))).isEqualTo("MLS-ID-007");
    }

    // UT-ID-006 高风险动作守卫：直呼拒绝，复核发起进入 PENDING
    @Test
    void utId006_highRiskChangeRequiresReview() {
        module.register(new RegisterReader("grace", "password123", "Grace"));
        var readerId = accounts.byUsername("grace").orElseThrow().id();

        assertThat(codeOf(module.changeAccount(new com.library.identity.contract.ChangeAccount(
                com.library.contract.model.AccountId.of(readerId), "SUSPEND",
                java.util.Map.of(), "须复核")))).isEqualTo("MLS-ID-006");
        assertThat(codeOf(module.changeAccount(new com.library.identity.contract.ChangeAccount(
                com.library.contract.model.AccountId.of(readerId), "CHANGE_ROLE",
                java.util.Map.of("newRole", "LIBRARIAN"), "须复核")))).isEqualTo("MLS-ID-006");
        // 状态未被绕过修改
        assertThat(accounts.byId(readerId).orElseThrow().status()).isEqualTo("ACTIVE");
    }

    private String codeOf(ModuleResult<?> result) {
        if (result instanceof ModuleResult.Rejected<?> rejected) {
            return rejected.code();
        }
        throw new AssertionError("期望业务拒绝，实际为 Accepted: " + result);
    }

    private boolean decision(ModuleResult<AuthorizationDecision> result) {
        if (result instanceof ModuleResult.Accepted<AuthorizationDecision> accepted) {
            return accepted.value().allowed();
        }
        // 授权拒绝以 Rejected 表达：视为不允许（原因码见对应 MLS-ID-*）
        return false;
    }
}
