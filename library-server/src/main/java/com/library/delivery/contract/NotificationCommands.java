package com.library.delivery.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;

public interface NotificationCommands {
    ModuleResult<ReadReceipt> markRead(long notificationId, ActorContext actor);
    record ReadReceipt(boolean read) {}
}
