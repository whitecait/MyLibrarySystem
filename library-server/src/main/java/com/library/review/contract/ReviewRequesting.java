package com.library.review.contract;

import com.library.contract.ModuleResult;

/**
 * REVIEW 复核发起能力（MLS-DOC-MD-0004 §2）。
 * B1 只承载账户变更申请的发起；决定与目标执行（ReviewDeciding/ReviewedAction）
 * 属 B3。不属高风险动作 → MLS-REV-003(422)。
 */
public interface ReviewRequesting {

    ModuleResult<Review> request(ReviewCommand command);
}
