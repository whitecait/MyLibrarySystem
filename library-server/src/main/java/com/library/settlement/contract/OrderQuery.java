package com.library.settlement.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;

public interface OrderQuery {
    ModuleResult<OrderCommands.Order> order(long orderId, ActorContext actor);
}
