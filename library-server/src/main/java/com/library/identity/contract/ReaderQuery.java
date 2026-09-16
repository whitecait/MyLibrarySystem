package com.library.identity.contract;

import com.library.contract.ModuleResult;
import com.library.identity.contract.model.ReaderId;

/**
 * IDENTITY 读者查询能力（MLS-DOC-MD-0004 §2）。
 * 读者不存在/不可见 → MLS-ID-005（HTTP 404 不可见对象呈现）。
 */
public interface ReaderQuery {

    ModuleResult<ReaderSnapshot> reader(ReaderId readerId);
}
