package com.library.review.contract;

/**
 * REVIEW 聚合接口：仅供 com.library.bootstrap 装配与一致性检查
 * （MLS-DOC-MD-0002 §3）。能力子接口随批次实现加入（B3：ReviewDeciding/ReviewQuery）。
 */
public interface ReviewModule extends ReviewRequesting, ReviewDeciding, ReviewQuery {
}
