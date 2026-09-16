package com.library.contract.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 统一取时（MLS-DOC-MD-0004 §5）：所有到期、业务日与重试判定取时走此接口，
 * 生产用系统时钟，测试用固定时钟。业务日以配置时区折算。
 */
public interface BusinessClock {

    ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    Instant now();

    default LocalDate today() {
        return LocalDate.ofInstant(now(), ZONE);
    }
}
