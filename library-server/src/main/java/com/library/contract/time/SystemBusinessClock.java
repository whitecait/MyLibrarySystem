package com.library.contract.time;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemBusinessClock implements BusinessClock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
