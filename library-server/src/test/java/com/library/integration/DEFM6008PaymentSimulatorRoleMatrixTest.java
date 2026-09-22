package com.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.identity.internal.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEF-M6-008 回归：POST /payment-simulator/results 控制器层角色矩阵。
 * 裁定依据（2026-09-22 lingzz，M6 恢复计划 §8 第 2 项）：契约 capability
 * CONTROLLED_TEST_OPERATOR 映射 ADMIN；模拟结果调度为管理员限定操作。
 * 断言：匿名 401（认证先行）/ READER 与 LIBRARIAN 403+MLS-ID-005 且不形成任何
 * 支付事实（订单仍占用、罚款未结清、无回调事件）/ ADMIN 200 且驱动订单终态；
 * 同时核对红线——读者正常支付建单权限不变（POST /fines/{fineId}/orders）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DEFM6008PaymentSimulatorRoleMatrixTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void schedulingMatrixIsAdminOnlyAndDoesNotFormFactsForOthers() throws Exception {
        var readerName = "def008-reader-" + System.nanoTime();
        var readerToken = registerAndLogin(readerName);
        long readerId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username=?", Long.class, readerName);
        var librarianToken = seedAndLogin("def008-librarian-" + System.nanoTime(), "LIBRARIAN");
        var adminToken = seedAndLogin("def008-admin-" + System.nanoTime(), "ADMIN");

        long fineId = seedDueFine(readerId, "def008-target");
        long orderId = seedOccupiedOrder(fineId);
        String scheduling = "{\"orderId\":\"%d\",\"result\":\"SUCCESS\",\"delayMs\":0,\"duplicateCount\":0}"
                .formatted(orderId);

        // 匿名访问保持 401：补角色检查不得改变认证先行顺序
        var anonymous = post("/api/v1/payment-simulator/results", null, "def008-anon", scheduling);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(code(anonymous)).isEqualTo("MLS-ID-001");

        // READER/LIBRARIAN 调度：403 + MLS-ID-005，且订单/罚款/回调事件均无变化
        assertForbidden(post("/api/v1/payment-simulator/results", readerToken, "def008-reader", scheduling));
        assertUnchanged(readerId, orderId);
        assertForbidden(post("/api/v1/payment-simulator/results", librarianToken, "def008-librarian", scheduling));
        assertUnchanged(readerId, orderId);

        // ADMIN 调度：200 且模拟回调经同步投递把订单置 PAID、罚款结清（AT-0016 支付链的调度动作）
        var scheduled = post("/api/v1/payment-simulator/results", adminToken, "def008-admin", scheduling);
        assertThat(scheduled.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(scheduled.getBody()).at("/data/status").asText()).isEqualTo("SCHEDULED");
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM fine WHERE id=?", String.class, fineId))
                .isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payment_callback_event WHERE order_id=? AND outcome='PAID'",
                Integer.class, orderId)).isEqualTo(1);

        // 红线：模拟调度限定不改变读者正常支付建单权限
        var secondReaderName = "def008-reader2-" + System.nanoTime();
        var secondReaderToken = registerAndLogin(secondReaderName);
        long secondReaderId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username=?", Long.class, secondReaderName);
        long secondFineId = seedDueFine(secondReaderId, "def008-redline");
        var created = post("/api/v1/fines/" + secondFineId + "/orders", secondReaderToken,
                "def008-order", "{}");
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(created.getBody()).at("/data/status").asText()).isEqualTo("OCCUPIED");
    }

    /** 越权调度不得形成支付事实：订单仍占用、罚款未结清、无回调事件行。 */
    private void assertUnchanged(long readerId, long orderId) {
        assertThat(orderStatus(orderId)).isEqualTo("OCCUPIED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM fine WHERE reader_id=?", String.class, readerId)).isEqualTo("DUE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payment_callback_event WHERE order_id=?",
                Integer.class, orderId)).isZero();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM payment_order WHERE id=?", String.class, orderId);
    }

    private String registerAndLogin(String username) throws Exception {
        var register = post("/api/v1/auth/registrations", null, "def008-reg-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                        .formatted(username, username));
        assertThat(register.getStatusCode().value()).isEqualTo(200);
        return login(username);
    }

    private String seedAndLogin(String username, String role) throws Exception {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, ?)
                """, username, PasswordHasher.hash("password123"), username, role);
        return login(username);
    }

    private String login(String username) throws Exception {
        var session = post("/api/v1/auth/sessions", null, "def008-login-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    /** 单读者未结罚款（数据库唯一索引 uq_fine_open_per_reader 限一条 DUE）。 */
    private long seedDueFine(long readerId, String prefix) {
        long titleId = jdbc.queryForObject(
                "INSERT INTO book_title(title,price_cents) VALUES (?,9900) RETURNING id",
                Long.class, prefix + "-" + UUID.randomUUID());
        long copyId = jdbc.queryForObject("""
                INSERT INTO book_copy(title_id,copy_no,status) VALUES (?,?,'SHELF_READY') RETURNING id
                """, Long.class, titleId, "ct-" + UUID.randomUUID().toString().substring(0, 12));
        long txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id,title_id,copy_id,status,reservation_date,due_date)
                VALUES (?,?,?,'RETURNED',current_date-40,current_date-10) RETURNING id
                """, Long.class, readerId, titleId, copyId);
        return jdbc.queryForObject("""
                INSERT INTO fine(reader_id,source_tx_id,amount_cents) VALUES (?,?,500) RETURNING id
                """, Long.class, readerId, txId);
    }

    private long seedOccupiedOrder(long fineId) {
        return jdbc.queryForObject("""
                INSERT INTO payment_order(fine_id,expires_at) VALUES (?,now()+interval '15 minutes')
                RETURNING id
                """, Long.class, fineId);
    }

    private void assertForbidden(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("MLS-ID-005");
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).path("code").asText();
    }

    private ResponseEntity<String> post(String path, String bearer, String idempotencyKey, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
