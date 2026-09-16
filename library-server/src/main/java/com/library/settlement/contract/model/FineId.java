package com.library.settlement.contract.model;

/** 罚款标识（对外十进制字符串）。 */
public record FineId(long value) {

    public FineId {
        if (value <= 0) {
            throw new IllegalArgumentException("fineId 必须为正数");
        }
    }

    public static FineId of(long value) {
        return new FineId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
