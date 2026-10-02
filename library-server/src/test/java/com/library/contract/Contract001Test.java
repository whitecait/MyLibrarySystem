package com.library.contract;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTRACT-001（MLS-DOC-MD-0005 §3.1）：OpenAPI 3.1 可解析、内部 $ref 完整、
 * 46 个 operationId 唯一。文档为唯一机器源，测试直接读取、不维护副本。
 */
class Contract001Test {

    private static final int OPERATION_COUNT = 46;

    @Test
    void openApiParsesAsOpenApi31() {
        OpenAPI api = parse();
        assertThat(api.getOpenapi()).startsWith("3.1");
        assertThat(api.getPaths()).isNotEmpty();
    }

    @Test
    void allInternalReferencesResolve() {
        assertThat(new OpenApiDocument().unresolvedRefs()).isEmpty();
    }

    @Test
    void operationIdsAreUnique() {
        OpenAPI api = parse();
        List<String> operationIds = new ArrayList<>();
        api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            if (operation.getOperationId() != null) {
                operationIds.add(operation.getOperationId());
            }
        }));
        assertThat(operationIds).hasSize(OPERATION_COUNT);
        assertThat(new HashSet<>(operationIds)).hasSize(OPERATION_COUNT);
    }

    @Test
    void retiredRefundAndReconciliationOperationsOnlyAdvertise422() {
        record RetiredOperation(String id, String path) {}
        var document = new OpenApiDocument();

        for (var retired : List.of(
                new RetiredOperation("requestRefund", "/orders/{orderId}/refunds"),
                new RetiredOperation("reconcileBusinessDate", "/reconciliations"))) {
            var operation = document.operation("post", retired.path());
            assertThat(operation.path("operationId").asText()).isEqualTo(retired.id());
            assertThat(operation.path("deprecated").asBoolean()).isTrue();
            assertThat(operation.path("x-permission").asText()).isEqualTo("UNSUPPORTED");
            assertThat(operation.path("security").isArray()).isTrue();
            assertThat(operation.path("security")).isEmpty();
            assertThat(operation.has("requestBody")).isFalse();
            assertThat(operation.path("responses").fieldNames()).toIterable().containsExactly("422");
            assertThat(document.responseSchema("post", retired.path(), 422)).isNotNull();
            assertThat(document.responseSchema("post", retired.path(), 200)).isNull();
        }
    }

    private OpenAPI parse() {
        SwaggerParseResult result = new OpenAPIParser().readLocation(
                OpenApiDocument.CONTRACT_PATH.toString(), null, new ParseOptions());
        assertThat(result.getOpenAPI())
                .withFailMessage("OpenAPI 解析失败: %s", result.getMessages())
                .isNotNull();
        return result.getOpenAPI();
    }
}
