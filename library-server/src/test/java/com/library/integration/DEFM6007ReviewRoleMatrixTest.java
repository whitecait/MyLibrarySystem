package com.library.integration;

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
 * DEF-M6-007 回归：复核三端点控制器层角色矩阵。
 * 契约（openapi.yaml x-permission）：GET /reviews＝LIBRARIAN_OR_ADMIN、
 * POST /reviews＝LIBRARIAN、POST /reviews/{id}/decision＝ADMIN（AT-0002 §2.3 R5）。
 * 无权组合断言 403/MLS-ID-005 且目标复核不落地任何决定；有权组合断言 200。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DEFM6007ReviewRoleMatrixTest {

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
    void roleMatrixOverThreeReviewEndpoints() throws Exception {
        var readerToken = registerAndLogin("def007-reader-" + System.nanoTime());
        var librarianName = "def007-librarian-" + System.nanoTime();
        var librarianToken = seedAndLogin(librarianName, "LIBRARIAN");
        var adminToken = seedAndLogin("def007-admin-" + System.nanoTime(), "ADMIN");
        long librarianId = jdbc.queryForObject(
                "SELECT id FROM account WHERE username=?", Long.class, librarianName);
        long pendingReview = seedPendingRefundReview(librarianId);

        // 匿名访问保持 401：补角色检查不得改变认证先行顺序
        var anonymous = get("/api/v1/reviews", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(code(anonymous)).isEqualTo("MLS-ID-001");

        // GET /reviews：READER 403；LIBRARIAN/ADMIN 200
        assertForbidden(get("/api/v1/reviews", readerToken));
        assertThat(get("/api/v1/reviews", librarianToken).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/reviews", adminToken).getStatusCode().value()).isEqualTo(200);

        // POST /reviews：READER/ADMIN 403；LIBRARIAN 200 且复核成立
        String request = "{\"targetType\":\"REFUND\",\"targetId\":\"1\",\"action\":\"REFUND\","
                + "\"payload\":{\"amountCents\":100},\"reason\":\"DEF-M6-007 矩阵\"}";
        assertForbidden(post("/api/v1/reviews", readerToken, "def007-post-reader", request));
        var librarianRequest = post("/api/v1/reviews", librarianToken, "def007-post-librarian", request);
        assertThat(librarianRequest.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(librarianRequest.getBody()).at("/data/status").asText())
                .isEqualTo("PENDING");
        assertForbidden(post("/api/v1/reviews", adminToken, "def007-post-admin", request));

        // POST /reviews/{id}/decision：READER/LIBRARIAN 403 且复核仍 PENDING；ADMIN 200 并落地决定
        String approve = "{\"decision\":\"APPROVED\",\"reason\":\"越权尝试必须被拒\"}";
        assertForbidden(post("/api/v1/reviews/" + pendingReview + "/decision",
                readerToken, "def007-dec-reader", approve));
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");
        assertForbidden(post("/api/v1/reviews/" + pendingReview + "/decision",
                librarianToken, "def007-dec-librarian", approve));
        assertThat(decisionOf(pendingReview)).isEqualTo("PENDING");
        var adminDecision = post("/api/v1/reviews/" + pendingReview + "/decision",
                adminToken, "def007-dec-admin", "{\"decision\":\"REJECTED\",\"reason\":\"有权决定\"}");
        assertThat(adminDecision.getStatusCode().value()).isEqualTo(200);
        assertThat(decisionOf(pendingReview)).isEqualTo("REJECTED");
    }

    private String registerAndLogin(String username) throws Exception {
        var register = post("/api/v1/auth/registrations", null, "def007-reg-" + username,
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
        var session = post("/api/v1/auth/sessions", null, "def007-login-" + username,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private long seedPendingRefundReview(long applicantId) {
        return jdbc.queryForObject("""
                INSERT INTO review_request(applicant_id,target_type,target_id,reason,action_type,
                                           action_payload,target_version)
                VALUES (?,'REFUND',1,'DEF-M6-007 矩阵种子','REFUND','{"amountCents":100}'::jsonb,1)
                RETURNING id
                """, Long.class, applicantId);
    }

    private String decisionOf(long reviewId) {
        return jdbc.queryForObject(
                "SELECT decision FROM review_request WHERE id=?", String.class, reviewId);
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
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        var headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
