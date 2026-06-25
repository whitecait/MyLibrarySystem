package com.library.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LibraryPropertiesTest {

    @Autowired
    private LibraryProperties props;

    @Test
    void shouldBindDefaultValues() {
        assertThat(props.getBorrow().getMaxLimit()).isEqualTo(5);
        assertThat(props.getBorrow().getDefaultDurationDays()).isEqualTo(30);
        assertThat(props.getBorrow().getAutoApproveDuration()).isEqualTo(30);
        assertThat(props.getBorrow().getMaxDurationCap()).isEqualTo(60);
        assertThat(props.getFine().getRatePerDay()).isEqualByComparingTo("1.0");
        assertThat(props.getRenewal().getAutoApproveCount()).isEqualTo(1);
    }
}
