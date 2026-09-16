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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-CAT-002（MLS-DOC-MD-0005 §4）：书目版本竞争（CAS，BR-0045）与移除约束
 * （仍有受约束事实 MLS-CAT-004；移除后不可见 MLS-CAT-002）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ITCAT002TitleVersionCompetitionTest {

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
    void concurrentUpdateSameVersionProducesOneWinnerAndOneConflict() throws Exception {
        var token = seedLibrarianAndLogin();
        var titleId = createTitle(token);

        int threads = 2;
        var latch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = List.copyOf(java.util.Collections.nCopies(threads, 0)).stream()
                    .<Future<ResponseEntity<String>>>map(ignored -> pool.submit(() -> {
                        latch.await();
                        return patch("/api/v1/titles/" + titleId, UUID.randomUUID().toString(), token,
                                "{\"title\":\"并发改-" + Thread.currentThread().getId() + "\",\"version\":0}");
                    }))
                    .toList();
            latch.countDown();
            int ok = 0;
            int conflict = 0;
            for (var future : futures) {
                var response = future.get();
                var body = json.readTree(response.getBody());
                if (response.getStatusCode().value() == 200) {
                    ok++;
                } else if (response.getStatusCode().value() == 409
                        && "MLS-CAT-003".equals(body.path("code").asText())) {
                    conflict++;
                    assertThat(body.at("/data/reasons").isArray()).isTrue();
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
            // 版本推进到 1，且不自动重试业务判定
            assertThat(jdbc.queryForObject(
                    "SELECT version FROM book_title WHERE id = ?", Long.class,
                    Long.parseLong(titleId))).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void removeIsBlockedByConstrainedFactsAndInvisibleAfterRemoval() throws Exception {
        var token = seedLibrarianAndLogin();

        // 有副本事实：移除被拒（MLS-CAT-004）
        var withCopy = createTitle(token);
        jdbc.update("INSERT INTO book_copy (title_id, copy_no) VALUES (?, 'C-1')",
                Long.parseLong(withCopy));
        var blocked = exchange("DELETE", "/api/v1/titles/" + withCopy, UUID.randomUUID().toString(), token,
                "{\"version\":0,\"reason\":\"尝试移除\"}");
        assertThat(blocked.getStatusCode().value()).isEqualTo(409);
        assertThat(json.readTree(blocked.getBody()).path("code").asText()).isEqualTo("MLS-CAT-004");

        // 无事实：移除成功，此后书目不可见（MLS-CAT-002，不泄漏已移除对象）
        var free = createTitle(token);
        var removed = exchange("DELETE", "/api/v1/titles/" + free, UUID.randomUUID().toString(), token,
                "{\"version\":0,\"reason\":\"无事实移除\"}");
        assertThat(removed.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(removed.getBody()).at("/data/status").asText()).isEqualTo("REMOVED");
        var gone = get("/api/v1/titles/" + free);
        assertThat(gone.getStatusCode().value()).isEqualTo(404);
        assertThat(json.readTree(gone.getBody()).path("code").asText()).isEqualTo("MLS-CAT-002");
    }

    private String seedLibrarianAndLogin() throws Exception {
        var username = "itcat002-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, 'LIBRARIAN')
                """, username, PasswordHasher.hash("password123"), username);
        var session = post("/api/v1/auth/sessions", "itcat002-login-" + username, null,
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username));
        assertThat(session.getStatusCode().value())
                .as("login 响应: %s", session.getBody()).isEqualTo(200);
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private String createTitle(String token) throws Exception {
        var created = post("/api/v1/titles", UUID.randomUUID().toString(), token,
                "{\"isbn\":\"%s\",\"title\":\"书-%s\",\"authors\":[\"作者\"],\"category\":\"计算机\",\"priceCents\":9900}"
                        .formatted("isbn" + UUID.randomUUID().toString().substring(0, 8),
                                UUID.randomUUID().toString().substring(0, 8)));
        assertThat(created.getStatusCode().value())
                .as("createTitle 响应: %s", created.getBody()).isEqualTo(200);
        return json.readTree(created.getBody()).at("/data/titleId").asText();
    }

    private ResponseEntity<String> post(String path, String idempotencyKey, String bearer, String body) {
        return exchange("POST", path, idempotencyKey, bearer, body);
    }

    private ResponseEntity<String> patch(String path, String idempotencyKey, String bearer, String body) {
        return exchange("PATCH", path, idempotencyKey, bearer, body);
    }

    private ResponseEntity<String> get(String path) {
        var headers = new HttpHeaders();
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchange(String method, String path, String idempotencyKey,
                                            String bearer, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, org.springframework.http.HttpMethod.valueOf(method),
                new HttpEntity<>(body, headers), String.class);
    }
}
