package com.library.identity.contract;

import com.library.contract.model.PageCursor;

/** 账户列表查询条件（role/status 可空过滤器 + 分页）。 */
public record AccountCriteria(String role, String status, PageCursor page) {
}
