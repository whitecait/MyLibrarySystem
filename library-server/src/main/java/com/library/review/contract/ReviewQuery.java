package com.library.review.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import java.time.Instant;
import java.util.List;

public interface ReviewQuery {
    ModuleResult<ReviewPage> reviews(ReviewCriteria query);
    ModuleResult<ReviewDetail> review(long reviewId);
    record ReviewCriteria(String status, PageCursor page) {}
    record ReviewSummary(long reviewId,String targetType,long targetId,String action,String status,Instant requestedAt) {}
    record ReviewPage(List<ReviewSummary> items,String nextCursor) {}
    record ReviewDetail(long reviewId,String targetType,long targetId,String action,java.util.Map<String,Object> payload,String status,long targetVersion) {}
}
