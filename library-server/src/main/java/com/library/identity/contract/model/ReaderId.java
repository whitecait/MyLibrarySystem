package com.library.identity.contract.model;

/** 读者标识（READER 角色账户；对外十进制字符串）。 */
public record ReaderId(long value) {

    public ReaderId {
        if (value <= 0) {
            throw new IllegalArgumentException("readerId 必须为正数");
        }
    }

    public static ReaderId of(long value) {
        return new ReaderId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
