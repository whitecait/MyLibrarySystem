package com.library.contract.time;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 固定时钟测试 Adapter（MLS-DOC-MD-0004 §5）：到期、业务日与重试判定的
 * 确定性取时；仅测试源码使用。
 */
public class FixedBusinessClock implements com.library.contract.time.BusinessClock {

    private volatile Instant current;

    public FixedBusinessClock(Instant initial) {
        this.current = initial;
    }

    public static FixedBusinessClock at(String isoInstant) {
        return new FixedBusinessClock(Instant.parse(isoInstant));
    }

    public void advance(java.time.Duration duration) {
        current = current.plus(duration);
    }

    public void set(Instant instant) {
        this.current = instant;
    }

    @Override
    public Instant now() {
        return current;
    }

    public LocalDate todayValue() {
        return today();
    }
}
