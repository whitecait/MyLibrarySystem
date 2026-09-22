package com.library.review.internal;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.contract.audit.AuditEventTypes;
import com.library.contract.audit.BusinessOutcome;
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
 * 成功路径审计（DEF-M6-011）：发起以 applicantId 为 actor、决定以 decidedBy 为 actor，
 * 使 AT-0002 §2.3 R5「L 发起与 S 复核可分别追溯」可按 actor_id 与时间核对。
 */
@Component
public class ReviewModuleImpl implements ReviewRequesting, com.library.review.contract.ReviewDeciding,
        com.library.review.contract.ReviewQuery {

    private static final Set<String> TARGET_TYPES = Set.of("ACCOUNT_CHANGE", "CIRCULATION_EXCEPTION", "REFUND");
    private static final Set<String> ACCOUNT_CHANGE_ACTIONS = Set.of("SUSPEND", "RESTORE", "CHANGE_ROLE");

    private final ReviewStore reviews;
    private final AuditAppender audit;

    public ReviewModuleImpl(ReviewStore reviews, AuditAppender audit) {
        this.reviews = reviews;
        this.audit = audit;
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
        audit.appendWithBusinessFact(AuditEventTypes.REVIEW_REQUESTED, command.applicantId(),
                new BusinessOutcome(id, "PENDING"));
        return ModuleResult.Accepted.of(new Review(id, "PENDING"));
    }

    @Override
    public ModuleResult<com.library.review.contract.ReviewDeciding.ReviewDecision> decide(
            com.library.review.contract.ReviewDeciding.DecideReview command,
            com.library.review.contract.ReviewedAction action) {
        var review=reviews.byId(command.reviewId());
        if(review.isEmpty() || !"PENDING".equals(review.get().decision()))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_002.code(),"复核不存在或已终结");
        var row=review.get();
        if("REJECTED".equals(command.decision())) {
            reviews.decide(row.id(),row.version(),"REJECTED",command.decidedBy(),null);
            audit.appendWithBusinessFact(AuditEventTypes.REVIEW_DECIDED, command.decidedBy(),
                    new BusinessOutcome(row.id(),"REJECTED"));
            return ModuleResult.Accepted.of(new com.library.review.contract.ReviewDeciding.ReviewDecision(row.id(),"REJECTED",null));
        }
        if(!"APPROVED".equals(command.decision()))
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_002.code(),"决定值无效");
        if(action.currentVersion()!=row.targetVersion())
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_REV_001.code(),action.currentVersion(),"目标已变化，请重新发起");
        var executed=action.execute(row.payload());
        if(executed instanceof ModuleResult.Rejected<String>)
            return ModuleResult.Rejected.of(StableErrorCode.MLS_REV_002.code(),"目标执行失败");
        var outcome=((ModuleResult.Accepted<String>)executed).value();
        if(!reviews.decide(row.id(),row.version(),"APPROVED",command.decidedBy(),outcome))
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_REV_001.code(),row.version()+1,"复核已变化");
        audit.appendWithBusinessFact(AuditEventTypes.REVIEW_DECIDED, command.decidedBy(),
                new BusinessOutcome(row.id(),"APPROVED"));
        return ModuleResult.Accepted.of(new com.library.review.contract.ReviewDeciding.ReviewDecision(row.id(),"APPROVED",outcome));
    }

    @Override
    public ModuleResult<com.library.review.contract.ReviewQuery.ReviewPage> reviews(
            com.library.review.contract.ReviewQuery.ReviewCriteria query) {
        var page=query.page()==null?com.library.contract.model.PageCursor.first():query.page();
        var after=page.cursor()==null?0:Long.parseLong(page.cursor());
        var rows=reviews.list(query.status(),after,page.limit()+1);
        var hasMore=rows.size()>page.limit();
        var items=rows.stream().limit(page.limit()).map(r->new com.library.review.contract.ReviewQuery.ReviewSummary(
                r.id(),r.targetType(),r.targetId(),r.action(),r.decision(),r.createdAt())).toList();
        return ModuleResult.Accepted.of(new com.library.review.contract.ReviewQuery.ReviewPage(items,
                hasMore?Long.toString(items.get(items.size()-1).reviewId()):null));
    }

    @Override public ModuleResult<com.library.review.contract.ReviewQuery.ReviewDetail> review(long id){return reviews.byId(id).<ModuleResult<com.library.review.contract.ReviewQuery.ReviewDetail>>map(r->ModuleResult.Accepted.of(new com.library.review.contract.ReviewQuery.ReviewDetail(r.id(),r.targetType(),r.targetId(),r.action(),r.payload(),r.decision(),r.targetVersion()))).orElseGet(()->ModuleResult.Rejected.of(StableErrorCode.MLS_REV_002.code(),"复核不存在"));}
}
