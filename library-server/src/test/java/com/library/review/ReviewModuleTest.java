package com.library.review;

import com.library.contract.ModuleResult;
import com.library.review.contract.*;
import com.library.review.internal.ReviewModuleImpl;
import com.library.review.internal.adapter.persistence.InMemoryReviewStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewModuleTest {
    @Test void utRev001_onlyClosedHighRiskTypesCanBeRequested() {
        var module=new ReviewModuleImpl(new InMemoryReviewStore());
        assertThat(module.request(new ReviewCommand(2,"UNKNOWN",9,"RUN",Map.of(),"理由",0))).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(accepted(module.request(new ReviewCommand(2,"REFUND",9,"REFUND",Map.of(),"理由",0))).status()).isEqualTo("PENDING");
    }
    @Test
    void utRev002_changedTargetIsRejectedWithoutExecutingAction() {
        var store=new InMemoryReviewStore();
        var module=new ReviewModuleImpl(store);
        var review=accepted(module.request(new ReviewCommand(2,"REFUND",9,"REFUND",Map.of("amountCents",500),"读者申请",3)));
        var executed=new AtomicBoolean();
        ReviewedAction action=new ReviewedAction(){ public long currentVersion(){return 4;} public ModuleResult<String> execute(Map<String,Object> payload){executed.set(true);return ModuleResult.Accepted.of("REFUNDED");}};

        var result=module.decide(new ReviewDeciding.DecideReview(review.reviewId(),"APPROVED","同意",10),action);

        assertThat(result).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(((ModuleResult.Rejected<?>)result).code()).isEqualTo("MLS-REV-001");
        assertThat(executed).isFalse();
    }
    @Test void utRev003_decisionIsTerminalAndApprovedActionRunsOnce() {
        var module=new ReviewModuleImpl(new InMemoryReviewStore());
        var review=accepted(module.request(new ReviewCommand(2,"REFUND",9,"REFUND",Map.of(),"理由",3)));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        ReviewedAction action=new ReviewedAction(){public long currentVersion(){return 3;}public ModuleResult<String> execute(Map<String,Object> p){calls.incrementAndGet();return ModuleResult.Accepted.of("REFUNDED");}};
        assertThat(accepted(module.decide(new ReviewDeciding.DecideReview(review.reviewId(),"APPROVED","同意",10),action)).status()).isEqualTo("APPROVED");
        assertThat(module.decide(new ReviewDeciding.DecideReview(review.reviewId(),"APPROVED","再次",10),action)).isInstanceOf(ModuleResult.Rejected.class);
        assertThat(calls).hasValue(1);
        var page=accepted(module.reviews(new ReviewQuery.ReviewCriteria("APPROVED",com.library.contract.model.PageCursor.first())));
        assertThat(page.items()).singleElement().extracting(ReviewQuery.ReviewSummary::status).isEqualTo("APPROVED");
    }
    @SuppressWarnings("unchecked") private static <T>T accepted(ModuleResult<T> r){return ((ModuleResult.Accepted<T>)r).value();}
}
