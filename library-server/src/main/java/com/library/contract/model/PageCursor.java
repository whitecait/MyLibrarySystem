package com.library.contract.model;

import com.library.contract.InfrastructureFailure;

/**
 * 列表分页游标（MD-0003 §1：limit 1~100，默认 20）。
 */
public record PageCursor(String cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;

    public PageCursor {
        if (limit < 1 || limit > 100) {
            throw new InfrastructureFailure("limit 超出 1~100");
        }
    }

    public static PageCursor first() {
        return new PageCursor(null, DEFAULT_LIMIT);
    }

    public static PageCursor of(String cursor, Integer limit) {
        return new PageCursor(cursor, limit == null ? DEFAULT_LIMIT : limit);
    }
}
