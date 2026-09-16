package com.library.catalog.contract;

import com.library.contract.ModuleResult;

/**
 * CATALOG 书目维护能力（MLS-DOC-MD-0004 §2）。
 * ISBN/版本冲突 → MLS-CAT-003(409)；删除仍有受约束事实 → MLS-CAT-004(409)。
 * 字段无效 → MLS-CAT-001(400)。
 */
public interface TitleCommands {

    ModuleResult<TitleChange> maintain(MaintainTitle command);
}
