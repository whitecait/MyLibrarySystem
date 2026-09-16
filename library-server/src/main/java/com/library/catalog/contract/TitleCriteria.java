package com.library.catalog.contract;

import com.library.contract.model.PageCursor;

/** 检索条件（q/category 可空；分页见 PageCursor）。 */
public record TitleCriteria(String q, String category, PageCursor page) {
}
