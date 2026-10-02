package com.library.contract.closing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWriteAdmissionGateTest {

    @Test
    void isolatedStateAllowsSessionIssueAndClosingControlButRejectsBusinessWrites() {
        var jdbc = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql) {
                return List.of(Map.of("status", "ISOLATED", "draining", true));
            }
        };
        var gate = new JdbcWriteAdmissionGate(jdbc);

        assertThat(gate.acquire("/api/v1/auth/sessions")).isTrue();
        assertThat(gate.acquire("/api/v1/closing/reopen")).isTrue();
        assertThat(gate.acquire("/api/v1/reservations")).isFalse();
    }
}
