package com.library.settlement.contract.model;

/** 金额（整数分；MD-0004 §1 值类型，所有者为 SETTLEMENT）。 */
public record MoneyCents(long value) implements java.io.Serializable {

    public MoneyCents {
        if (value < 0) {
            throw new IllegalArgumentException("金额不可为负");
        }
    }

    public static MoneyCents of(long value) {
        return new MoneyCents(value);
    }
}
