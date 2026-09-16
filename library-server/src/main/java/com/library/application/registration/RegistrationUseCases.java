package com.library.application.registration;

import com.library.identity.contract.AccountRegistration;
import com.library.identity.contract.IdentityRegistration;
import com.library.identity.contract.RegisterReader;
import com.library.contract.ModuleResult;
import org.springframework.stereotype.Component;

/**
 * 注册应用用例（MLS-DOC-MD-0004 §3）：账户与读者资料整体提交。
 * B0 探针切片只编排 IdentityRegistration；事务由幂等包装层开启，
 * 本用例经 REQUIRED 传播加入同一 JDBC 事务。
 */
public interface RegistrationUseCases {

    ModuleResult<AccountRegistration> register(RegisterReader command);
}

@Component
record RegistrationUseCasesImpl(IdentityRegistration identity) implements RegistrationUseCases {

    RegistrationUseCasesImpl {
        if (identity == null) {
            throw new IllegalArgumentException("IdentityRegistration 不可缺省");
        }
    }

    @Override
    public ModuleResult<AccountRegistration> register(RegisterReader command) {
        return identity.register(command);
    }
}
