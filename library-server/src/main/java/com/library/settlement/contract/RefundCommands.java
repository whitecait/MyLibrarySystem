package com.library.settlement.contract;

import com.library.contract.ModuleResult;

public interface RefundCommands {
    ModuleResult<Refund> refund(ApprovedRefundCommand command);

    record ApprovedRefundCommand(long orderId, long amountCents, long reviewId) {}
    record Refund(long refundId, long orderId, long amountCents, String status) {}
}
