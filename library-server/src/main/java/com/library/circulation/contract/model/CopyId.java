package com.library.circulation.contract.model;

/** 实体副本标识（对外十进制字符串）。 */
public record CopyId(long value) {

    public CopyId {
        if (value <= 0) {
            throw new IllegalArgumentException("copyId 必须为正数");
        }
    }

    public static CopyId of(long value) {
        return new CopyId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
