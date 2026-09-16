package com.library.identity.contract;

import com.library.contract.model.AccountId;
import com.library.identity.contract.model.AccountStatus;

/**
 * 注册结果（OpenAPI RegistrationData：accountId、role=READER、status=ACTIVE）。
 */
public record AccountRegistration(AccountId accountId,
                                  com.library.contract.model.Role role,
                                  AccountStatus status) {
}
