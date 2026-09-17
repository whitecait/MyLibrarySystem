package com.library.delivery.contract;

public interface DeliveryModule extends NotificationQuery, NotificationCommands, AuditQuery, OutboxConsumer {}
