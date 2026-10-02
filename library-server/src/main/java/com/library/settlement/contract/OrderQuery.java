package com.library.settlement.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;

import java.util.List;

public interface OrderQuery {
    ModuleResult<OrderCommands.Order> order(long orderId, ActorContext actor);
    ModuleResult<OrderPage> occupiedOrders(PageCursor page, ActorContext actor);

    record OrderPage(List<OrderCommands.Order> items, String nextCursor) {}
}
