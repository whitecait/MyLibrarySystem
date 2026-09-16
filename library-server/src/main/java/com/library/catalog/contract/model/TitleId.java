package com.library.catalog.contract.model;

/** 书目标识（对外十进制字符串）。 */
public record TitleId(long value) {

    public TitleId {
        if (value <= 0) {
            throw new IllegalArgumentException("titleId 必须为正数");
        }
    }

    public static TitleId of(long value) {
        return new TitleId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
