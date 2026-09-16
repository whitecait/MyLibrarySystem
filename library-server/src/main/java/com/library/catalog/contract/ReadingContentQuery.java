package com.library.catalog.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.catalog.contract.model.TitleId;

/**
 * CATALOG 阅读内容读取能力（MLS-DOC-MD-0004 §2）。
 * 无公开内容 → MLS-CAT-005(404)。
 */
public interface ReadingContentQuery {

    ModuleResult<ReadingContent> readingContent(TitleId titleId, PageCursor page);
}
