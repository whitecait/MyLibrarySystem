package com.library.catalog.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.catalog.contract.model.TitleId;
import com.library.contract.security.Visibility;

/**
 * CATALOG 查询能力（MLS-DOC-MD-0004 §2）。
 * 书目不存在/已移除 → MLS-CAT-002(404)；查询无效 → MLS-CAT-001(400)。
 */
public interface TitleQuery {

    ModuleResult<TitlePage> search(TitleCriteria query, Visibility visibility);

    ModuleResult<TitleView> title(TitleId titleId, Visibility visibility);
}
