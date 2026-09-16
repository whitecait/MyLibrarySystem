package com.library.identity.contract;

import java.util.List;

/**
 * 账户列表结果（OpenAPI AccountPageData：items/nextCursor）。
 */
public record AccountPage(List<AccountSummary> items, String nextCursor) {
}
