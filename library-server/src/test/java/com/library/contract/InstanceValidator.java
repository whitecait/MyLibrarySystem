package com.library.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CONTRACT-003 实例校验：以 OpenAPI 文档内 schema（2020-12 方言）校验
 * 请求/响应实例。schema 先经 $ref 展开，避免外部 URI 基解析。
 */
public final class InstanceValidator {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private InstanceValidator() {
    }

    public static Set<ValidationMessage> validate(JsonNode expandedSchema, JsonNode instance) {
        JsonSchema schema = FACTORY.getSchema(expandedSchema);
        return new LinkedHashSet<>(schema.validate(instance));
    }

    public static String describe(Set<ValidationMessage> violations) {
        var builder = new StringBuilder();
        for (var violation : violations) {
            builder.append(violation.getInstanceLocation()).append(": ")
                    .append(violation.getMessage()).append("; ");
        }
        return builder.toString();
    }
}
