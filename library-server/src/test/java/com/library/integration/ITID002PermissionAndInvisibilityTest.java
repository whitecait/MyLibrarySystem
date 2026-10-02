package com.library.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.identity.internal.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-ID-002（MLS-DOC-MD-0005 §4）：401/403/404 权限与不可见性 + 拒绝审计
 * （ACCESS_DENIED 以独立事务登记，DD-0004）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ITID002PermissionAndInvisibilityTest {

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

    private String registerAndLogin(String username) throws Exception {
        var register = post("/api/v1/auth/registrations", null, "it-id002-reg-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"%s\"}"
                        .formatted(username, username));
        if (register.getStatusCode().value() != 200) {
            throw new IllegalStateException("注册失败: " + register.getBody());
        }
        var session = post("/api/v1/auth/sessions", null, "it-id002-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private void seedLibrarian(String username) {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, 'LIBRARIAN')
                """, username, PasswordHasher.hash("password123"), username);
    }

    @Test
    void permissionMatrixAndInvisibilityAndDenialAudit() throws Exception {
        var readerName = "it002-reader-" + System.nanoTime();
        var readerToken = registerAndLogin(readerName);
        var readerId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, readerName);
        var librarianName = "it002-librarian-" + System.nanoTime();
        seedLibrarian(librarianName);
        var librarianToken = login(librarianName);

        // 401：未携带令牌访问受保护操作
        var anonymous = rest.getForEntity("/api/v1/accounts", String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(anonymous.getBody()).path("code").asText()).isEqualTo("MLS-ID-001");

        // 401：无效令牌（拦截器拒绝 + 拒绝审计）
        var bogus = get("/api/v1/accounts", "not-a-real-token");
        assertThat(bogus.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(bogus.getBody()).path("code").asText()).isEqualTo("MLS-ID-001");

        // 403：READER 访问 ADMIN 操作（角色不足，MLS-ID-005）
        var forbidden = get("/api/v1/accounts", readerToken);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(json.readTree(forbidden.getBody()).path("code").asText()).isEqualTo("MLS-ID-005");

        // 200：LIBRARIAN……不，listAccounts 需要 ADMIN——LIBRARIAN 也应 403
        var librarianForbidden = get("/api/v1/accounts", librarianToken);
        assertThat(librarianForbidden.getStatusCode().value()).isEqualTo(403);

        // 404：馆员对不可见读者变更状态（目标不存在，MLS-ID-007 呈现 404）
        var invisibleReader = patch("/api/v1/readers/999999/status", librarianToken,
                "it002-invisible-" + System.nanoTime(),
                "{\"status\":\"SUSPENDED\",\"reason\":\"不存在目标\"}");
        assertThat(invisibleReader.getStatusCode().value()).isEqualTo(404);
        assertThat(json.readTree(invisibleReader.getBody()).path("code").asText()).isEqualTo("MLS-ID-007");

        // 账户复核只针对现存目标；缺失目标在申请阶段返回 MLS-ID-007。
        var pendingReview = post("/api/v1/accounts/" + readerId + "/change-requests", librarianToken,
                "it002-review-" + System.nanoTime(),
                "{\"action\":\"SUSPEND\",\"payload\":{},\"reason\":\"验证已存在账户的复核流程\"}");
        assertThat(pendingReview.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(pendingReview.getBody()).at("/data/status").asText()).isEqualTo("PENDING");

        // 拒绝审计独立存在（DD-0004）
        var auditRows = jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE event_type = 'ACCESS_DENIED'", Integer.class);
        assertThat(auditRows).isGreaterThanOrEqualTo(3);

        // 正向对照：ADMIN 可列账户（种子 ADMIN）
        var adminName = "it002-admin-" + System.nanoTime();
        seedAdmin(adminName);
        var adminToken = login(adminName);
        var ok = get("/api/v1/accounts", adminToken);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(ok.getBody()).at("/data/items").isArray()).isTrue();
    }

    @Test
    void nonEmptyFinesRemainOwnedByTheAuthenticatedReaderDespiteAForeignFilter() throws Exception {
        var suffix = Long.toString(System.nanoTime());
        var ownerName = "p3-fine-owner-" + suffix;
        var otherName = "p3-fine-other-" + suffix;
        var emptyName = "p3-fine-empty-" + suffix;
        var ownerToken = registerAndLogin(ownerName);
        var otherToken = registerAndLogin(otherName);
        var emptyToken = registerAndLogin(emptyName);
        var ownerId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, ownerName);
        var otherId = jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, otherName);
        var ownerFine = seedFine(ownerId, 300L);
        var otherFine = seedFine(otherId, 700L);
        var librarianName = "p3-fine-librarian-" + suffix;
        seedLibrarian(librarianName);
        var librarianToken = login(librarianName);

        // Positive control: the foreign fine exists and is visible to a librarian.
        var foreignControl = get("/api/v1/fines?readerId=" + otherId, librarianToken);
        assertThat(foreignControl.getStatusCode().value()).isEqualTo(200);
        var foreignItems = json.readTree(foreignControl.getBody()).at("/data/items");
        assertThat(foreignItems.size()).isEqualTo(1);
        assertThat(foreignItems.get(0).path("fineId").asLong()).isEqualTo(otherFine);
        assertThat(foreignItems.get(0).path("amountCents").asLong()).isEqualTo(700L);

        for (var path : java.util.List.of("/api/v1/fines", "/api/v1/fines?readerId=" + otherId,
                "/api/v1/fines?readerId=")) {
            var response = get(path, ownerToken);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            var items = json.readTree(response.getBody()).at("/data/items");
            assertThat(items.size()).isEqualTo(1);
            assertThat(items.get(0).path("fineId").asLong()).isEqualTo(ownerFine);
            assertThat(items.get(0).path("readerId").asLong()).isEqualTo(ownerId);
        }
        var otherResponse = get("/api/v1/fines?readerId=" + ownerId, otherToken);
        assertThat(otherResponse.getStatusCode().value()).isEqualTo(200);
        var otherItems = json.readTree(otherResponse.getBody()).at("/data/items");
        assertThat(otherItems.size()).isEqualTo(1);
        assertThat(otherItems.get(0).path("fineId").asLong()).isEqualTo(otherFine);

        var emptyResponse = get("/api/v1/fines?readerId=" + otherId, emptyToken);
        assertThat(emptyResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(emptyResponse.getBody()).at("/data/items").size()).isZero();
        for (var path : java.util.List.of("/api/v1/fines", "/api/v1/fines?readerId=")) {
            var response = get(path, librarianToken);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            var ids = new java.util.ArrayList<Long>();
            json.readTree(response.getBody()).at("/data/items")
                    .forEach(item -> ids.add(item.path("fineId").asLong()));
            assertThat(ids).contains(ownerFine, otherFine);
        }
    }

    private long seedFine(long readerId, long amountCents) {
        var titleId = jdbc.queryForObject(
                "INSERT INTO book_title(title, price_cents) VALUES ('P3 fine isolation', 1000) RETURNING id",
                Long.class);
        var txId = jdbc.queryForObject("""
                INSERT INTO borrow_transaction(reader_id, title_id, status)
                VALUES (?, ?, 'RETURNED') RETURNING id
                """, Long.class, readerId, titleId);
        return jdbc.queryForObject("""
                INSERT INTO fine(reader_id, source_tx_id, amount_cents)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, readerId, txId, amountCents);
    }

    private void seedAdmin(String username) {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, 'ADMIN')
                """, username, PasswordHasher.hash("password123"), username);
    }

    private String login(String username) throws Exception {
        var session = post("/api/v1/auth/sessions", null, "it-id002-login-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private ResponseEntity<String> post(String path, String bearer, String idempotencyKey, String body) {
        return exchange("POST", path, bearer, idempotencyKey, body);
    }

    private ResponseEntity<String> patch(String path, String bearer, String idempotencyKey, String body) {
        return exchange("PATCH", path, bearer, idempotencyKey, body);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        var headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchange(String method, String path, String bearer,
                                            String idempotencyKey, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.valueOf(method),
                new HttpEntity<>(body, headers), String.class);
    }
}
