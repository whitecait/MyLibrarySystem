package com.library.circulation.contract.model;

import java.time.LocalDate;

/** 业务日（YYYY-MM-DD；MD-0004 §1 值类型，所有者为 CIRCULATION）。 */
public record BusinessDate(LocalDate value) implements java.io.Serializable {

    public BusinessDate {
        java.util.Objects.requireNonNull(value, "业务日不可为空");
    }

    public static BusinessDate of(LocalDate value) {
        return new BusinessDate(value);
    }

    public static BusinessDate parse(String iso) {
        return new BusinessDate(LocalDate.parse(iso));
    }

    public String asString() {
        return value.toString();
    }
}
