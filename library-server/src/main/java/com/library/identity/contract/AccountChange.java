package com.library.identity.contract;

/**
 * 账户维护结果快照（状态/角色/版本摘要）。
 */
public record AccountChange(com.library.contract.model.AccountId accountId,
                            String status,
                            String role,
                            long version) {
}
