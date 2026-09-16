package com.library.identity;

import com.library.contract.ModuleResult;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.RegisterReader;
import com.library.identity.internal.IdentityModuleImpl;
import com.library.identity.internal.PasswordHasher;
import com.library.identity.internal.adapter.persistence.InMemoryAccountStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B0 注册切片单元测试（从模块接口观察，不读内部表结构；
 * UT-ID 正式编号用例在 B1 登记）。
 */
class IdentityRegistrationModuleTest {

    private final IdentityModuleImpl module = new IdentityModuleImpl(new InMemoryAccountStore());

    @Test
    void registersReaderWithHashedPassword() {
        var result = module.register(new RegisterReader("alice", "password123", "Alice"));
        assertThat(result).isInstanceOf(ModuleResult.Accepted.class);
        var registration = ((ModuleResult.Accepted<AccountRegistration>) result).value();
        assertThat(registration.role().name()).isEqualTo("READER");
        assertThat(registration.status().name()).isEqualTo("ACTIVE");
        assertThat(registration.accountId().asString()).isNotBlank();
    }

    @Test
    void rejectsDuplicateUsernameWithStableCode() {
        module.register(new RegisterReader("bob", "password123", "Bob"));
        var rejected = module.register(new RegisterReader("bob", "password456", "Bob II"));
        assertThat(rejected).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(codeOf(rejected)).isEqualTo("MLS-ID-002");
    }

    @Test
    void rejectsInvalidFieldsWithStableCode() {
        assertThat(codeOf(module.register(new RegisterReader("", "password123", "X")))).isEqualTo("MLS-ID-003");
        assertThat(codeOf(module.register(new RegisterReader("carol", "short", "X")))).isEqualTo("MLS-ID-003");
        assertThat(codeOf(module.register(new RegisterReader("dave", "password123", null)))).isEqualTo("MLS-ID-003");
        assertThat(codeOf(module.register(new RegisterReader("eve", "password123", "x".repeat(101)))))
                .isEqualTo("MLS-ID-003");
    }

    @Test
    void passwordHashRoundTrips() {
        var hash = PasswordHasher.hash("password123");
        assertThat(PasswordHasher.matches("password123", hash)).isTrue();
        assertThat(PasswordHasher.matches("password124", hash)).isFalse();
    }

    @Test
    void rejectedRegistrationLeavesNoAccount() {
        var store = new InMemoryAccountStore();
        var isolated = new IdentityModuleImpl(store);
        isolated.register(new RegisterReader("frank", "short", "Frank"));
        assertThat(store.usernameExists("frank")).isFalse();
        isolated.register(new RegisterReader("frank", "password123", "Frank"));
        assertThat(store.usernameExists("frank")).isTrue();
    }

    private String codeOf(ModuleResult<AccountRegistration> result) {
        if (result instanceof ModuleResult.Rejected<AccountRegistration> rejected) {
            return rejected.code();
        }
        throw new AssertionError("期望业务拒绝，实际为 Accepted");
    }
}
