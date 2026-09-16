package com.library.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 共同样例加载器（G0）：读取已批准的 contract-scenarios.json 及其 schema，
 * 作为后端契约测试与联调的同一输入，不复制样例内容。
 */
public final class ContractScenarios {

    public static final Path SCENARIOS_PATH = Path.of(
            "..", "docs", "04-module-design", "contracts", "examples", "contract-scenarios.json");
    public static final Path SCHEMA_PATH = Path.of(
            "..", "docs", "04-module-design", "contracts", "examples", "contract-scenarios.schema.json");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode root;
    private final OpenApiDocument schemaDocument;

    public ContractScenarios() {
        this(readTree(SCENARIOS_PATH), new OpenApiDocument(readTree(SCHEMA_PATH)));
    }

    public ContractScenarios(JsonNode root, OpenApiDocument schemaDocument) {
        this.root = root;
        this.schemaDocument = schemaDocument;
    }

    private static JsonNode readTree(Path path) {
        try {
            return JSON.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取共同样例: " + path, e);
        }
    }

    public JsonNode root() {
        return root;
    }

    public List<JsonNode> all() {
        var scenarios = new ArrayList<JsonNode>();
        root.get("scenarios").forEach(scenarios::add);
        return scenarios;
    }

    public JsonNode byId(String id) {
        return all().stream().filter(s -> id.equals(s.path("id").asText())).findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少共同样例: " + id));
    }

    /** 样例文件外形必须通过相邻 schema 校验（MD-0003 §6）；schema 描述整份文档。 */
    public List<String> shapeViolations() {
        var schema = schemaDocument.expand(schemaDocument.root());
        var violations = new ArrayList<String>();
        var messages = InstanceValidator.validate(schema, root);
        messages.forEach(message -> violations.add("contract-scenarios: " + message.getMessage()));
        return violations;
    }

    /** 场景请求对应的 OpenAPI 操作路径（去 /api/v1 前缀与查询串）。 */
    public String operationPath(JsonNode scenario) {
        var raw = scenario.at("/request/path").asText().replaceFirst("^/api/v1", "");
        int query = raw.indexOf('?');
        return query >= 0 ? raw.substring(0, query) : raw;
    }

    /** 场景请求的查询参数（key=value），无查询串返回空映射。 */
    public java.util.LinkedHashMap<String, String> queryOf(JsonNode scenario) {
        var raw = scenario.at("/request/path").asText();
        int query = raw.indexOf('?');
        var params = new java.util.LinkedHashMap<String, String>();
        if (query < 0) {
            return params;
        }
        for (var pair : raw.substring(query + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                params.put(pair.substring(0, equals), pair.substring(equals + 1));
            } else if (!pair.isEmpty()) {
                params.put(pair, "");
            }
        }
        return params;
    }

    public String operationMethod(JsonNode scenario) {
        return scenario.at("/request/method").asText().toLowerCase();
    }
}
