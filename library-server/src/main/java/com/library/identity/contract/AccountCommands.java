package com.library.identity.contract;

import com.library.contract.ModuleResult;

/**
 * IDENTITY 账户维护能力（MLS-DOC-MD-0004 §2）。
 * 高风险动作（SUSPEND/RESTORE/CHANGE_ROLE）只由复核批准事务执行（B3）；
 * READER_STATUS 馆员直办；状态冲突 MLS-ID-006(409)。
 */
public interface AccountCommands {

    ModuleResult<AccountChange> changeAccount(ChangeAccount command);

    /** 密码重置（LIBRARIAN/ADMIN 直办；返回临时密码，forceChange 恒 true）。 */
    ModuleResult<TemporaryPassword> resetPassword(ResetPassword command);
}
