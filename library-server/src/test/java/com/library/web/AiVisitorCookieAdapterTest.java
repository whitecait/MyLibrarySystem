package com.library.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiVisitorCookieAdapterTest {
    @Test void signingKeyRequiresAtLeastThirtyTwoBytes() {
        assertThatThrownBy(()->new AiVisitorCookieAdapter("short-secret"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        assertThatThrownBy(()->new AiVisitorCookieAdapter(" ".repeat(32)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        assertThat(new AiVisitorCookieAdapter("x".repeat(32)).recoverable("key").raw()).isNotBlank();
    }
}
