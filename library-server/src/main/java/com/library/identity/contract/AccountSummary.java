package com.library.identity.contract;

/**
 * 账户摘要（OpenAPI AccountSummaryData：accountId/username/displayName/role/status/version）。
 */
public record AccountSummary(com.library.contract.model.AccountId accountId,
                             String username,
                             String displayName,
                             String role,
                             String status,
                             long version) {
}
