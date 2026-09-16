package com.library.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTRACT-003 样例形状门禁：9 个共同样例自身通过 schema 校验，
 * 且每个场景的请求/响应结构与 OpenAPI 当前文档逐操作一致。
 * 场景的活体执行随批次接入（本批 registerReader 见 RegistrationContractProbeTest）。
 */
class Contract003ScenarioShapeTest {

    private final OpenApiDocument document = new OpenApiDocument();
    private final ContractScenarios scenarios = new ContractScenarios();

    @Test
    void nineScenariosArePresent() {
        assertThat(scenarios.all()).hasSize(9);
        var ids = new LinkedHashSet<String>();
        scenarios.all().forEach(scenario -> ids.add(scenario.path("id").asText()));
        assertThat(ids).hasSize(9);
    }

    @Test
    void scenariosConformToTheirOwnSchema() {
        assertThat(scenarios.shapeViolations()).isEmpty();
    }

    @Test
    void everyScenarioRequestMatchesOpenApiOperation() {
        for (JsonNode scenario : scenarios.all()) {
            var method = scenarios.operationMethod(scenario);
            var path = scenarios.operationPath(scenario);
            assertThat(document.operation(method, path))
                    .as("场景 %s 的操作 %s %s 必须存在于 OpenAPI", scenario.path("id").asText(), method, path)
                    .isNotNull();

            for (String required : document.requiredHeaders(method, path)) {
                assertThat(scenario.at("/request/headers").has(required))
                        .as("场景 %s 缺少必需头 %s", scenario.path("id").asText(), required)
                        .isTrue();
            }

            assertQueryParametersDeclared(scenario, method, path);

            var requestBody = scenario.at("/request/body");
            if (!requestBody.isMissingNode()) {
                var schema = document.requestSchema(method, path);
                assertThat(schema).as("场景 %s 期望请求 schema", scenario.path("id").asText()).isNotNull();
                var violations = InstanceValidator.validate(schema, requestBody);
                assertThat(violations)
                        .as("场景 %s 请求体不符合 OpenAPI: %s", scenario.path("id").asText(),
                                InstanceValidator.describe(violations))
                        .isEmpty();
            }
        }
    }

    private void assertQueryParametersDeclared(JsonNode scenario, String method, String path) {
        var declared = new java.util.HashSet<String>();
        var parameters = document.operation(method, path).get("parameters");
        if (parameters != null && parameters.isArray()) {
            parameters.forEach(parameter -> {
                var expanded = document.expand(parameter);
                if ("query".equals(expanded.path("in").asText())) {
                    declared.add(expanded.path("name").asText());
                }
            });
        }
        scenarios.queryOf(scenario).forEach((name, value) -> {
            assertThat(declared.contains(name))
                    .as("场景 %s 的查询参数 %s 未在操作中声明", scenario.path("id").asText(), name)
                    .isTrue();
        });
    }

    @Test
    void everyScenarioResponseMatchesOpenApiOperation() {
        for (JsonNode scenario : scenarios.all()) {
            var method = scenarios.operationMethod(scenario);
            var path = scenarios.operationPath(scenario);
            int status = scenario.at("/response/httpStatus").asInt();

            var schema = document.responseSchema(method, path, status);
            assertThat(schema)
                    .as("场景 %s 的状态 %d 必须在 OpenAPI 响应或 default 中", scenario.path("id").asText(), status)
                    .isNotNull();

            var responseBody = scenario.at("/response/body");
            var violations = InstanceValidator.validate(schema, responseBody);
            assertThat(violations)
                    .as("场景 %s 响应体不符合 OpenAPI: %s", scenario.path("id").asText(),
                            InstanceValidator.describe(violations))
                    .isEmpty();

            // 成功/错误信封的 code 必须与场景一致地出现在 data 约束外层（结构一致性）
            assertThat(responseBody.has("code")).isTrue();
            assertThat(responseBody.has("data")).isTrue();
        }
    }

    @Test
    void errorScenarioStatusesMatchRegisteredCodes() {
        // 409 类样例：错误码与 HTTP 状态必须落在 MD-0003 登记映射内
        record Expectation(String id, int status) {
        }
        for (Expectation expectation : java.util.List.of(
                new Expectation("ORDER-CONCURRENT-CONFLICT", 409),
                new Expectation("REVIEW-TARGET-CHANGED", 409))) {
            var scenario = scenarios.byId(expectation.id());
            assertThat(scenario.at("/response/httpStatus").asInt()).isEqualTo(expectation.status());
        }
        Set<String> errorCodes = Set.of("MLS-PAY-001", "MLS-REV-001");
        for (JsonNode scenario : scenarios.all()) {
            int status = scenario.at("/response/httpStatus").asInt();
            var code = scenario.at("/response/body/code").asText();
            if (status != 200) {
                assertThat(errorCodes.contains(code) || code.equals("MLS-AI-002"))
                        .as("场景 %s 的错误码 %s 应为登记稳定码", scenario.path("id").asText(), code)
                        .isTrue();
            }
        }
    }
}
