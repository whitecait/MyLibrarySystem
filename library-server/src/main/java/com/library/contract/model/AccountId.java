package com.library.contract.model;

/**
 * 账户标识（十进制字符串对外；进程内用 long 包装值）。
 * 放在共享 contract.model：ActorContext 的构成类型，属共享身份语义。
 */
public record AccountId(long value) implements java.io.Serializable {

    public AccountId {
        if (value <= 0) {
            throw new IllegalArgumentException("accountId 必须为正数");
        }
    }

    public static AccountId of(long value) {
        return new AccountId(value);
    }

    /** 对外十进制字符串（MD-0003 §1）。 */
    public String asString() {
        return Long.toString(value);
    }
}
