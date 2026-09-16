package com.library.review.contract;

/**
 * 复核结果（OpenAPI ReviewReferenceData：reviewId/status=PENDING）。
 */
public record Review(long reviewId, String status) {
}
