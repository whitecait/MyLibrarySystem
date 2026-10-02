package com.library.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.ContractScenarios;
import com.library.contract.InstanceValidator;
import com.library.contract.OpenApiDocument;
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

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B0 契约探针（B0-GATE）：健康接口之外，registerReader 垂直切片经真实 HTTP
 * 与真实 PostgreSQL 执行，共同样例 AUTH-REGISTER-SUCCESS 为权威输入；
 * 请求/响应按 OpenAPI 逐操作校验（CONTRACT-003 活体部分），
 * 幂等重放/摘要冲突覆盖 X2 的单线程语义。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationContractProbeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:f3bd19c606e442c3d7bdfa8002e03fe260a1023351e0ea4598032022b68dd6e3");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private final OpenApiDocument contract = new OpenApiDocument();
    private final ContractScenarios scenarios = new ContractScenarios();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void contractScenarioExecutesLiveAndMatchesOpenApi() throws Exception {
        var scenario = scenarios.byId("AUTH-REGISTER-SUCCESS");
        var response = post(scenario.at("/request/path").asText(),
                scenario.at("/request/headers/Idempotency-Key").asText(),
                scenario.at("/request/body").toString());

        assertThat(response.getStatusCode().value())
                .isEqualTo(scenario.at("/response/httpStatus").asInt());
        var body = json.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo(scenario.at("/response/body/code").asText());
        assertThat(body.has("message")).isTrue();

        var schema = contract.responseSchema("post", "/auth/registrations", 200);
        var violations = InstanceValidator.validate(schema, body);
        assertThat(violations)
                .withFailMessage("响应不符合 OpenAPI: %s", InstanceValidator.describe(violations))
                .isEmpty();
    }

    @Test
    void sameKeySameBodyReplaysFirstResult() throws Exception {
        var key = "probe-" + UUID.randomUUID();
        var username = "replay-" + UUID.randomUUID();
        var body = json.createObjectNode()
                .put("username", username)
                .put("password", "password123")
                .put("displayName", "Replay Probe");
        var first = post("/api/v1/auth/registrations", key, body.toString());
        var second = post("/api/v1/auth/registrations", key, body.toString());

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        var firstBody = json.readTree(first.getBody());
        var secondBody = json.readTree(second.getBody());
        assertThat(secondBody.path("code").asText()).isEqualTo(firstBody.path("code").asText());
        assertThat(secondBody.at("/data/accountId").asText())
                .isEqualTo(firstBody.at("/data/accountId").asText());
        // 效果唯一：重放不创建第二个账户
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username = ?",
                Integer.class, username)).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyIsRejectedWithIdempotencyConflict() throws Exception {
        var key = "probe-" + UUID.randomUUID();
        var first = json.createObjectNode()
                .put("username", "conflict-" + UUID.randomUUID())
                .put("password", "password123")
                .put("displayName", "Conflict Probe");
        assertThat(post("/api/v1/auth/registrations", key, first.toString()).getStatusCode().value())
                .isEqualTo(200);

        var conflicting = json.createObjectNode()
                .put("username", "conflict-" + UUID.randomUUID())
                .put("password", "password123")
                .put("displayName", "Conflict Probe");
        var second = post("/api/v1/auth/registrations", key, conflicting.toString());
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json.readTree(second.getBody()).path("code").asText()).isEqualTo("MLS-IDEMP-002");
    }

    @Test
    void duplicateUsernameIsRejectedWithConflict() throws Exception {
        var username = "dup-" + UUID.randomUUID();
        assertThat(post("/api/v1/auth/registrations", "probe-" + UUID.randomUUID(),
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"Dup\"}".formatted(username))
                .getStatusCode().value()).isEqualTo(200);

        var second = post("/api/v1/auth/registrations", "probe-" + UUID.randomUUID(),
                "{\"username\":\"%s\",\"password\":\"password456\",\"displayName\":\"Dup II\"}".formatted(username));
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json.readTree(second.getBody()).path("code").asText()).isEqualTo("MLS-ID-002");
    }

    @Test
    void invalidFieldsAreRejectedWithFieldError() throws Exception {
        var response = post("/api/v1/auth/registrations", "probe-" + UUID.randomUUID(),
                "{\"username\":\"field-%s\",\"password\":\"short\",\"displayName\":\"Field\"}"
                        .formatted(UUID.randomUUID()));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json.readTree(response.getBody()).path("code").asText()).isEqualTo("MLS-ID-003");
    }

    @Test
    void registrationCountsFailedAttemptsAndBlocksSixthRequestForServerIssuedSession() throws Exception {
        var cookie = (String) null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            var response = post("/api/v1/auth/registrations", "rate-limit-invalid-" + UUID.randomUUID(),
                    "{\"username\":\"rate-invalid-" + attempt + "-" + UUID.randomUUID()
                            + "\",\"password\":\"short\",\"displayName\":\"Invalid\"}", cookie,
                    "198.51.100." + attempt);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            if (cookie == null) {
                var setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
                assertThat(setCookie).contains("; Path=/; HttpOnly; SameSite=Strict");
                cookie = setCookie.substring((RegistrationRateLimitFilter.COOKIE_NAME + "=").length(),
                        setCookie.indexOf(';'));
            }
        }

        var acceptedUsername = "rate-limit-fifth-" + UUID.randomUUID();
        var fifth = post("/api/v1/auth/registrations", "rate-limit-fifth-" + UUID.randomUUID(),
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"Fifth\"}"
                        .formatted(acceptedUsername), cookie, "203.0.113.5");
        assertThat(fifth.getStatusCode().value()).isEqualTo(200);

        var blockedUsername = "rate-limit-sixth-" + UUID.randomUUID();
        var sixth = post("/api/v1/auth/registrations", "rate-limit-sixth-" + UUID.randomUUID(),
                "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"Sixth\"}"
                        .formatted(blockedUsername), cookie, "203.0.113.6");
        assertThat(sixth.getStatusCode().value()).isEqualTo(429);
        assertThat(json.readTree(sixth.getBody()).path("code").asText()).isEqualTo("MLS-ID-008");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username = ?", Integer.class,
                acceptedUsername)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username = ?", Integer.class,
                blockedUsername)).isZero();
    }

    @Test
    void concurrentRequestsShareAtomicSessionQuota() throws Exception {
        var firstInvalid = post("/api/v1/auth/registrations", "parallel-seed-" + UUID.randomUUID(),
                "{\"username\":\"parallel-seed\",\"password\":\"short\",\"displayName\":\"Invalid\"}");
        assertThat(firstInvalid.getStatusCode().value()).isEqualTo(400);
        var setCookie = firstInvalid.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        var cookie = setCookie.substring((RegistrationRateLimitFilter.COOKIE_NAME + "=").length(),
                setCookie.indexOf(';'));

        var ready = new CountDownLatch(6);
        var start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(6);
        try {
            var requests = java.util.stream.IntStream.range(0, 6).mapToObj(index ->
                    CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        try {
                            if (!start.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("registration quota start barrier timed out");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                        var username = "parallel-register-" + index + "-" + UUID.randomUUID();
                        var body = "{\"username\":\"%s\",\"password\":\"password123\",\"displayName\":\"Parallel\"}"
                                .formatted(username);
                        var response = post("/api/v1/auth/registrations", "parallel-" + UUID.randomUUID(),
                                body, cookie, "198.51.100." + (index + 10));
                        return Map.entry(username, response);
                    }, workers)).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var results = requests.stream().map(CompletableFuture::join).toList();
            assertThat(results.stream().filter(result -> result.getValue().getStatusCode().value() == 200))
                    .hasSize(4);
            assertThat(results.stream().filter(result -> result.getValue().getStatusCode().value() == 429))
                    .hasSize(2);
            for (var result : results) {
                var expectedAccounts = result.getValue().getStatusCode().value() == 200 ? 1 : 0;
                assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username=?", Integer.class,
                        result.getKey())).isEqualTo(expectedAccounts);
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void missingIdempotencyKeyIsRejectedAsFieldError() throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.postForEntity("/api/v1/auth/registrations",
                new HttpEntity<>("{\"username\":\"nokey-" + UUID.randomUUID()
                        + "\",\"password\":\"password123\",\"displayName\":\"NoKey\"}", headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private ResponseEntity<String> post(String path, String idempotencyKey, String body) {
        return post(path, idempotencyKey, body, null);
    }

    private ResponseEntity<String> post(String path, String idempotencyKey, String body, String sessionCookie) {
        return post(path, idempotencyKey, body, sessionCookie, null);
    }

    private ResponseEntity<String> post(String path, String idempotencyKey, String body,
                                        String sessionCookie, String forwardedFor) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, RegistrationRateLimitFilter.COOKIE_NAME + "=" + sessionCookie);
        }
        if (forwardedFor != null) headers.set("X-Forwarded-For", forwardedFor);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }
}
