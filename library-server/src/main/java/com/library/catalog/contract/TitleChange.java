package com.library.catalog.contract;

/**
 * 书目维护结果（action 后的目标聚合摘要；REMOVE 时 status=REMOVED）。
 */
public record TitleChange(String action,
                          long titleId,
                          long version,
                          String status) {
}
