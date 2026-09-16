package com.library.circulation.contract.model;

/** 借阅事务标识（对外十进制字符串）。 */
public record TxId(long value) {

    public TxId {
        if (value <= 0) {
            throw new IllegalArgumentException("txId 必须为正数");
        }
    }

    public static TxId of(long value) {
        return new TxId(value);
    }

    public String asString() {
        return Long.toString(value);
    }
}
