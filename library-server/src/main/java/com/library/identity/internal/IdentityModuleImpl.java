package com.library.identity.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.RegisterReader;
import com.library.identity.contract.model.AccountStatus;
import com.library.identity.internal.adapter.persistence.AccountStore;
import org.springframework.stereotype.Component;

/**
 * IDENTITY 模块实现（B0：注册能力）。规则与持久化细节隐藏在模块内；
 * 调用方只经能力子接口观察 Accepted/Rejected。
 */
@Component
public class IdentityModuleImpl implements IdentityRegistration {

    private final AccountStore accounts;

    public IdentityModuleImpl(AccountStore accounts) {
        this.accounts = accounts;
    }

    @Override
    public ModuleResult<AccountRegistration> register(RegisterReader command) {
        var fieldRejection = validate(command);
        if (fieldRejection != null) {
            return fieldRejection;
        }
        if (accounts.usernameExists(command.username())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_ID_002.code(), "用户名已存在");
        }
        var stored = accounts.insert(new AccountStore.NewAccount(
                command.username(), PasswordHasher.hash(command.password()), command.displayName()));
        return ModuleResult.Accepted.of(new AccountRegistration(
                com.library.contract.model.AccountId.of(stored.id()),
                com.library.contract.model.Role.READER,
                AccountStatus.valueOf(stored.status())));
    }

    private ModuleResult.Rejected<AccountRegistration> validate(RegisterReader command) {
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
}
