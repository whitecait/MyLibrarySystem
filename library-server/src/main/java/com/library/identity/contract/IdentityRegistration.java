package com.library.identity.contract;

import com.library.contract.ModuleResult;

/**
 * IDENTITY 注册能力接口（MLS-DOC-MD-0004 §2）。
 * 完成全部守卫才返回 Accepted；Rejected 不产生状态变化。
 * 错误码：MLS-ID-002 重名、MLS-ID-003 字段无效。
 */
public interface IdentityRegistration {

    ModuleResult<AccountRegistration> register(RegisterReader command);
}
