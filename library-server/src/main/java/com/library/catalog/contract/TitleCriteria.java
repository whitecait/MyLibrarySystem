package com.library.catalog.contract;

import com.library.contract.model.PageCursor;

/** 检索条件；availability 可选 AVAILABLE|UNAVAILABLE|UNKNOWN。 */
public record TitleCriteria(String q, String category, String availability, PageCursor page) {

    public TitleCriteria(String q, String category, PageCursor page) {
        this(q, category, null, page);
    }
}
