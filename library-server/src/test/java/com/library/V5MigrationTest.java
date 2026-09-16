package com.library;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5 迁移测试（B1 差额处置）：auth_session 唯一令牌与吊销语义、
 * 阅读内容页主键与页码约束、复核动作快照列、publish_year 往返。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class V5MigrationTest {

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
    @Autowired Flyway flyway;

    @Test
    void v5AppliesOnceAndValidationStaysClean() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '5' AND success = true
                """, Integer.class)).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void authSessionTokenIsUniqueAndRevocationIsExplicit() {
        long account = insertReader("v5-session-reader");
        long first = insertSession(account, "hash-v5-1");
        insertSession(account, "hash-v5-2"); // 同账户多会话合法
        assertThatThrownBy(() -> insertSession(account, "hash-v5-1"))
                .isInstanceOf(DataIntegrityViolationException.class); // token_hash 唯一
        jdbc.update("UPDATE auth_session SET ended_at = now() WHERE id = ?", first);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auth_session WHERE id = ? AND ended_at IS NOT NULL",
                Integer.class, first)).isEqualTo(1);
    }

    @Test
    void contentPagesAreKeyedByTitleAndPageNoWithPositivePage() {
        long title = insertTitle("v5-content-book");
        jdbc.update("""
                INSERT INTO book_title_content_page (title_id, page_no, content_type, text)
                VALUES (?, 1, 'text/plain', '第一页'), (?, 2, 'text/plain', '第二页')
                """, title, title);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO book_title_content_page (title_id, page_no, content_type, text)
                VALUES (?, 1, 'text/plain', '重复页')
                """, title)).isInstanceOf(DataIntegrityViolationException.class); // 主键去重
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO book_title_content_page (title_id, page_no, content_type, text)
                VALUES (?, 0, 'text/plain', '零页')
                """, title)).isInstanceOf(DataIntegrityViolationException.class); // 页码 >= 1
    }

    @Test
    void reviewRequestCarriesActionSnapshotColumns() {
        long librarian = insertReader("v5-review-librarian");
        jdbc.update("""
                INSERT INTO review_request (applicant_id, target_type, target_id, reason,
                                            action_type, action_payload, target_version)
                VALUES (?, 'ACCOUNT_CHANGE', ?, '违规账号', 'SUSPEND', '{"role":"READER"}'::jsonb, 3)
                """, librarian, librarian);
        var row = jdbc.queryForMap("""
                SELECT action_type, action_payload::text AS payload, target_version, target_outcome
                FROM review_request WHERE applicant_id = ?
                """, librarian);
        assertThat(row.get("action_type")).isEqualTo("SUSPEND");
        assertThat((String) row.get("payload")).contains("READER");
        assertThat(row.get("target_version")).isEqualTo(3L);
        assertThat(row.get("target_outcome")).isNull();
    }

    @Test
    void publishYearRoundTripsOnBookTitle() {
        long title = insertTitle("v5-year-book");
        jdbc.update("UPDATE book_title SET publish_year = 2001 WHERE id = ?", title);
        assertThat(jdbc.queryForObject(
                "SELECT publish_year FROM book_title WHERE id = ?", Integer.class, title)).isEqualTo(2001);
    }

    // ---- fixtures ----

    private long insertReader(String username) {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, 'x', ?, 'READER')
                """, username, username);
        return jdbc.queryForObject("SELECT id FROM account WHERE username = ?", Long.class, username);
    }

    private long insertTitle(String name) {
        jdbc.update("INSERT INTO book_title (title, price_cents) VALUES (?, 10000)", name);
        return jdbc.queryForObject("SELECT id FROM book_title WHERE title = ?", Long.class, name);
    }

    private long insertSession(long accountId, String tokenHash) {
        return jdbc.queryForObject("""
                INSERT INTO auth_session (account_id, token_hash, expires_at)
                VALUES (?, ?, now() + interval '1 day') RETURNING id
                """, Long.class, accountId, tokenHash);
    }
}
