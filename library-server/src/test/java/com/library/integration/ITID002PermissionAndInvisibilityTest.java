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
        var readerToken = registerAndLogin("it002-reader-" + System.nanoTime());
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

        // 账户变更申请：MD-0003 只登记状态冲突，目标有效性由 B3 批准事务重验（MLS-REV-001）
        var pendingReview = post("/api/v1/accounts/999999/change-requests", librarianToken,
                "it002-review-" + System.nanoTime(),
                "{\"action\":\"SUSPEND\",\"payload\":{},\"reason\":\"目标有效性由批准重验\"}");
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
