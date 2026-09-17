package com.library.application.reviewing;

import com.library.contract.ModuleResult;
import com.library.contract.model.*;
import com.library.contract.security.ActorContext;
import com.library.review.contract.*;
import com.library.settlement.contract.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

@Component
public final class SettlementReviewedActionProvider implements ReviewedActionProvider {
    private final OrderQuery orders; private final RefundCommands refunds;
    public SettlementReviewedActionProvider(OrderQuery o,RefundCommands r){orders=o;refunds=r;}
    @Override public ReviewedAction actionFor(ReviewQuery.ReviewDetail d){if(!"REFUND".equals(d.targetType())||!"REFUND".equals(d.action()))return new ReviewedAction(){public long currentVersion(){return d.targetVersion();}public ModuleResult<String> execute(Map<String,Object> p){return ModuleResult.Rejected.of("MLS-REV-002","尚未登记的复核动作");}};return new ReviewedAction(){public long currentVersion(){var r=orders.order(d.targetId(),new ActorContext(AccountId.of(1),Role.SYSTEM,Instant.EPOCH));return r instanceof ModuleResult.Accepted<OrderCommands.Order>a?a.value().version():-1;}public ModuleResult<String> execute(Map<String,Object> p){var amount=((Number)p.get("amountCents")).longValue();var r=refunds.refund(new RefundCommands.ApprovedRefundCommand(d.targetId(),amount,d.reviewId()));if(r instanceof ModuleResult.Accepted<RefundCommands.Refund>)return ModuleResult.Accepted.of("REFUNDED");return ModuleResult.Rejected.of("MLS-REV-002","退款目标执行失败");}};}
}
