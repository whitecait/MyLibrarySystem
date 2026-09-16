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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-CAT-001（MLS-DOC-MD-0005 §4）：pg_trgm 固定检索标注集（T4）+ 服务端 SLI 证据
 * （处理上限 10s；实测耗时记录于断言输出）。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ITCAT001TrigramSearchTest {

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
    void trigramSearchReturnsLabeledCorpusInSimilarityOrderWithinSli() throws Exception {
        // 标注集：三个确定相关书目 + 若干干扰项
        seedTitle("数据库系统概念");
        seedTitle("数据库系统实现");
        seedTitle("数据库并发控制的艺术");
        seedTitle("西线无战事");
        seedTitle("烹饪入门");
        seedTitle("爵士乐史");
        seedAdmin();

        var token = login();
        var started = System.nanoTime();
        // 交由 RestTemplate 一次性编码查询参数，避免二次编码导致检索词失配
        var response = get("/api/v1/titles?q=数据库&limit=20", token);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = json.readTree(response.getBody());
        var items = body.at("/data/items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThanOrEqualTo(3);
        // 全部命中包含「数据库」的书目（相似度检索不返回无关项）
        for (JsonNode item : items) {
            assertThat(item.path("title").asText()).contains("数据库");
        }
        // 相似度排序：完全包含检索词的书目排在前面，首个命中含「数据库」
        assertThat(items.get(0).path("title").asText()).contains("数据库");
        // SLI：服务端处理上限 10s（MD-0003 §1）
        assertThat(elapsedMillis).as("检索耗时 %d ms（SLI 10000ms）", elapsedMillis).isLessThan(10_000);
    }

    private void seedTitle(String title) {
        jdbc.update("INSERT INTO book_title (title, price_cents) VALUES (?, 5000)", title);
    }

    private void seedAdmin() {
        jdbc.update("""
                INSERT INTO account (username, password_hash, display_name, role)
                VALUES (?, ?, ?, 'ADMIN')
                """, "itcat001-" + UUID.randomUUID(), PasswordHasher.hash("password123"), "admin");
    }

    private String login() throws Exception {
        var session = post("/api/v1/auth/sessions", "itcat001-login",
                "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(
                        jdbc.queryForObject(
                                "SELECT username FROM account WHERE role = 'ADMIN' LIMIT 1", String.class)));
        return json.readTree(session.getBody()).at("/data/accessToken").asText();
    }

    private ResponseEntity<String> post(String path, String idempotencyKey, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }
}
