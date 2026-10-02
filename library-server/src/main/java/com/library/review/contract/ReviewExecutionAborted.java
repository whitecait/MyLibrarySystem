package com.library.review.contract;

/**
 * A review action or final decision write failed after entering the decision transaction.
 * The exception forces the surrounding idempotency unit of work to roll back before the
 * controller maps it back to a stable business error.
 */
public final class ReviewExecutionAborted extends RuntimeException {
    private final String code;
    private final Long currentVersion;

    public ReviewExecutionAborted(String code, String message, Long currentVersion) {
        super(message);
        this.code = code;
        this.currentVersion = currentVersion;
    }

    public String code() {
        return code;
    }

    public Long currentVersion() {
        return currentVersion;
    }
}
