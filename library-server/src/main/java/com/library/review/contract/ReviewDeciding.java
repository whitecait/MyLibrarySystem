package com.library.review.contract;

import com.library.contract.ModuleResult;

public interface ReviewDeciding {
    ModuleResult<ReviewDecision> decide(DecideReview command, ReviewedAction action);
    record DecideReview(long reviewId, String decision, String reason, long decidedBy) {}
    record ReviewDecision(long reviewId, String status, String targetOutcome) {}
}
