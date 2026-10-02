package com.library.identity.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;

/**
 * IDENTITY 账户列表查询能力（GET /accounts 的承载；ADMIN）。
 */
public interface AccountQuery {

    ModuleResult<AccountPage> accounts(AccountCriteria query);

    /** Exact role/status/version snapshot for revalidating a pending account review. */
    ModuleResult<AccountState> accountState(com.library.contract.model.AccountId accountId);
}
