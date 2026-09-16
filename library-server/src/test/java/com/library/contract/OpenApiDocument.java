package com.library.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 契约单一输入（G0/B0-GATE）：后端测试直接读取已批准的 OpenAPI 文档，
 * 不维护任何副本。提供原始树、$ref 完整性检查与 $ref 展开。
 */
public final class OpenApiDocument {

    public static final Path CONTRACT_PATH = Path.of(
            "..", "docs", "04-module-design", "contracts", "http", "openapi.yaml");

    private final JsonNode root;
    private final YAMLMapper mapper = new YAMLMapper();

    public OpenApiDocument() {
        this(readTree(CONTRACT_PATH));
    }

    public OpenApiDocument(JsonNode root) {
        this.root = root;
    }

    private static JsonNode readTree(Path path) {
        try {
            return new YAMLMapper().readTree(Files.readString(path));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 OpenAPI 文档: " + path, e);
        }
    }

    public JsonNode root() {
        return root;
    }

    public JsonNode pathItem(String path) {
        return root.at("/paths").get(path);
    }

    public JsonNode operation(String method, String path) {
        var item = pathItem(path);
        if (item == null) {
            item = pathItemByTemplate(path);
        }
        return item == null ? null : item.get(method.toLowerCase());
    }

    /** 模板匹配：{param} 段匹配任意非空实际段（样例路径携带具体 ID）。 */
    private JsonNode pathItemByTemplate(String actualPath) {
        var actualSegments = actualPath.split("/");
        var paths = root.at("/paths");
        for (var iterator = paths.fieldNames(); iterator.hasNext(); ) {
            var template = iterator.next();
            var templateSegments = template.split("/");
            if (templateSegments.length != actualSegments.length) {
                continue;
            }
            var matched = true;
            for (int i = 0; i < templateSegments.length; i++) {
                var segment = templateSegments[i];
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    if (actualSegments[i].isEmpty()) {
                        matched = false;
                        break;
                    }
                } else if (!segment.equals(actualSegments[i])) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return paths.get(template);
            }
        }
        return null;
    }

    public JsonNode operationByOperationId(String operationId) {
        var paths = root.at("/paths");
        for (var iterator = paths.fieldNames(); iterator.hasNext(); ) {
            var path = iterator.next();
            var item = paths.get(path);
            for (var fieldIterator = item.fieldNames(); fieldIterator.hasNext(); ) {
                var method = fieldIterator.next();
                var operation = item.get(method);
                if (operation.isObject() && operationId.equals(operation.path("operationId").asText())) {
                    return operation;
                }
            }
        }
        return null;
    }

    /** 解析 operation 的请求体 schema（application/json），无请求体返回 null。 */
    public JsonNode requestSchema(String method, String path) {
        var operation = operation(method, path);
        if (operation == null) {
            return null;
        }
        var requestBody = operation.get("requestBody");
        if (requestBody == null || requestBody.isNull()) {
            return null;
        }
        var schema = requestBody.at("/content/application/json/schema");
        return expand(schema);
    }

    /** 解析指定状态响应 schema；该状态未登记返回 null，default 兜底返回 ErrorEnvelope。 */
    public JsonNode responseSchema(String method, String path, int status) {
        var operation = operation(method, path);
        if (operation == null) {
            return null;
        }
        var responses = operation.get("responses");
        var response = responses.get(String.valueOf(status));
        if (response == null || response.isNull()) {
            response = responses.get("default");
        }
        if (response == null || response.isNull()) {
            return null;
        }
        // 响应节点可能是 $ref（各 *Response），先展开再取 schema
        var expanded = expand(response);
        var schema = expanded.at("/content/application/json/schema");
        return expand(schema);
    }

    /** 操作声明的必需 header 参数名（如 Idempotency-Key），含 path-item 级参数。 */
    public List<String> requiredHeaders(String method, String path) {
        var names = new ArrayList<String>();
        var item = pathItem(path);
        collectRequiredHeaders(item == null ? null : item.get("parameters"), names);
        var operation = operation(method, path);
        if (operation != null) {
            collectRequiredHeaders(operation.get("parameters"), names);
        }
        return names;
    }

    private void collectRequiredHeaders(JsonNode parameters, List<String> names) {
        if (parameters == null || !parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            var expanded = expand(parameter);
            if ("header".equals(expanded.path("in").asText()) && expanded.path("required").asBoolean()) {
                names.add(expanded.path("name").asText());
            }
        }
    }

    /** CONTRACT-001 引用完整性：文档内全部 $ref 必须可解析。 */
    public List<String> unresolvedRefs() {
        var violations = new ArrayList<String>();
        collectUnresolved(root, "", violations);
        return violations;
    }

    private void collectUnresolved(JsonNode node, String at, List<String> violations) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            var ref = ((ObjectNode) node).get("$ref");
            if (ref != null) {
                var target = root.at(ref.asText().substring(1));
                if (target.isMissingNode()) {
                    violations.add(at + " -> " + ref.asText());
                }
            }
            var fields = ((ObjectNode) node).fieldNames();
            for (var iterator = fields; iterator.hasNext(); ) {
                var field = iterator.next();
                collectUnresolved(((ObjectNode) node).get(field), at + "/" + field, violations);
            }
        } else if (node.isArray()) {
            var array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                collectUnresolved(array.get(i), at + "/" + i, violations);
            }
        }
    }

    /** 递归展开 $ref（文档无循环引用；深度上限防御性保护）。 */
    public JsonNode expand(JsonNode node) {
        return expand(node, 0);
    }

    private JsonNode expand(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 64) {
            return node;
        }
        if (node.isObject()) {
            var ref = ((ObjectNode) node).get("$ref");
            if (ref != null) {
                return expand(root.at(ref.asText().substring(1)), depth + 1);
            }
            ObjectNode out = mapper.createObjectNode();
            var fields = ((ObjectNode) node).fieldNames();
            for (var iterator = fields; iterator.hasNext(); ) {
                var field = iterator.next();
                out.set(field, expand(((ObjectNode) node).get(field), depth + 1));
            }
            return out;
        }
        if (node.isArray()) {
            var array = (ArrayNode) node;
            var out = mapper.createArrayNode();
            for (JsonNode item : array) {
                out.add(expand(item, depth + 1));
            }
            return out;
        }
        return node.deepCopy();
    }
}
