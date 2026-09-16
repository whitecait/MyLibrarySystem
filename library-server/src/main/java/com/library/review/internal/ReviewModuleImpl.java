package com.library.review.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.review.contract.Review;
import com.library.review.contract.ReviewCommand;
import com.library.review.contract.ReviewRequesting;
import com.library.review.internal.adapter.persistence.ReviewStore;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * REVIEW 模块实现（B1：发起；B3 扩展决定与目标执行）。
 * 只有已批准的高风险动作类型可以发起复核。
 */
@Component
public class ReviewModuleImpl implements ReviewRequesting {

    private static final Set<String> TARGET_TYPES = Set.of("ACCOUNT_CHANGE", "CIRCULATION_EXCEPTION", "REFUND");
    private static final Set<String> ACCOUNT_CHANGE_ACTIONS = Set.of("SUSPEND", "RESTORE", "CHANGE_ROLE");

    private final ReviewStore reviews;

    public ReviewModuleImpl(ReviewStore reviews) {
        this.reviews = reviews;
    }

    @Override
    public ModuleResult<Review> request(ReviewCommand command) {
        if (command.targetType() == null || !TARGET_TYPES.contains(command.targetType())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_003.code(), "不属高风险动作类型");
        }
        var action = command.action() == null ? "" : command.action();
        var actionAllowed = switch (command.targetType()) {
            case "ACCOUNT_CHANGE" -> ACCOUNT_CHANGE_ACTIONS.contains(action);
            case "CIRCULATION_EXCEPTION" -> true; // B2 人工处置转复核的具体动作随批次登记
            case "REFUND" -> true;                // 退款动作随 B3 退款发起登记
            default -> false;
        };
        if (!actionAllowed) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_003.code(), "不属高风险动作");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_003.code(), "复核必须附理由");
        }
        var id = reviews.insert(new ReviewStore.NewReview(command.applicantId(), command.targetType(),
                command.targetId(), action, command.payload(), command.reason(), command.targetVersion()));
        return ModuleResult.Accepted.of(new Review(id, "PENDING"));
    }
}
