package com.library.web;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1 契约接合：共同样例 CATALOG-PUBLIC-SEARCH 以真实 HTTP 执行
 * （种子含样例检索词 q=database 的命中书目），响应按 OpenAPI 逐操作校验。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogScenarioContractTest {

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

    private final OpenApiDocument contract = new OpenApiDocument();
    private final ContractScenarios scenarios = new ContractScenarios();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void catalogPublicSearchScenarioExecutesLive() throws Exception {
        // 种子：样例检索 q=database 的确定命中（含样例书目名）
        jdbc.update("""
                INSERT INTO book_title (title, author, category, price_cents)
                VALUES ('database系统概念', '作者', '计算机', 9900)
                """);
        jdbc.update("INSERT INTO book_title (title, price_cents) VALUES ('与检索词无关的书', 100)");

        var scenario = scenarios.byId("CATALOG-PUBLIC-SEARCH");
        var path = scenario.at("/request/path").asText();
        var response = rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode().value())
                .isEqualTo(scenario.at("/response/httpStatus").asInt());
        var body = json.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo(scenario.at("/response/body/code").asText());

        var schema = contract.responseSchema("get", "/titles", 200);
        var violations = InstanceValidator.validate(schema, body);
        assertThat(violations)
                .withFailMessage("响应不符合 OpenAPI: %s", InstanceValidator.describe(violations))
                .isEmpty();

        // 书目级可用性投影：只出现书目级枚举，不泄露逐册状态
        var items = body.at("/data/items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        for (var item : items) {
            assertThat(item.path("availability").asText()).isIn("AVAILABLE", "UNAVAILABLE", "UNKNOWN");
            assertThat(item.has("copyStatus")).isFalse();
        }
        // 命中包含种子书目
        var titles = new java.util.ArrayList<String>();
        items.forEach(item -> titles.add(item.path("title").asText()));
        assertThat(titles).contains("database系统概念");
        assertThat(titles).doesNotContain("与检索词无关的书");
    }
}
