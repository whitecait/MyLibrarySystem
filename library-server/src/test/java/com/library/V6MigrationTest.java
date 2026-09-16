package com.library;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** V6 迁移测试（B2）：借阅事务续借计数列存在且默认 0。 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class V6MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void borrowTransactionCarriesRenewalCountDefaultZero() {
        var reader = jdbc.queryForObject("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES ('v6-reader', 'x', 'v6', 'READER') RETURNING id
                """, Long.class);
        var titleId = jdbc.queryForObject("""
                INSERT INTO book_title (title, price_cents) VALUES ('v6-book', 100) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO borrow_transaction (reader_id, title_id, status, reservation_date)
                VALUES (?, ?, 'AWAIT', current_date)
                """, reader, titleId);
        var count = jdbc.queryForObject(
                "SELECT renewal_count FROM borrow_transaction WHERE reader_id = ?", Integer.class, reader);
        assertThat(count).isZero();
    }
}
