package com.library.review.internal.adapter.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.time.Instant;

/**
 * REVIEW 持久化内部缝（MLS-DOC-MD-0004 §4）：复核版本、决定与目标执行关联。
 */
public interface ReviewStore {

    long insert(NewReview candidate);

    Optional<StoredReview> byId(long reviewId);
    List<StoredReview> list(String decision, long afterId, int limit);
    boolean decide(long reviewId, long version, String decision, long decidedBy, String targetOutcome);

    record NewReview(long applicantId, String targetType, long targetId, String action,
                     Map<String, Object> payload, String reason, long targetVersion) {
    }

    record StoredReview(long id, long applicantId, String targetType, long targetId, String action,
                        Map<String, Object> payload, String reason, String decision,
                        long targetVersion, String targetOutcome, long version, Instant createdAt) {
    }
}
