package com.library.delivery.contract.model;

/** 通知标识（对外十进制字符串）。 */
public record NotificationId(long value) {

    public NotificationId {
        if (value <= 0) {
            throw new IllegalArgumentException("notificationId 必须为正数");
        }
    }

    public static NotificationId of(long value) {
        return new NotificationId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
