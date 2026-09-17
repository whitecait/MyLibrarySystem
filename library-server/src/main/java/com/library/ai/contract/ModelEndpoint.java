package com.library.ai.contract;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 外部模型边界，不暴露供应商 SDK 类型。 */
public interface ModelEndpoint {
    ModelResponse complete(ModelRequest request) throws ModelFailure;

    record ModelRequest(List<Map<String, Object>> messages, List<AiTool> tools,
                        Map<String, String> thinking, Duration timeout) {
        public ModelRequest { messages = List.copyOf(messages); tools = List.copyOf(tools); thinking = Map.copyOf(thinking); }
    }
    record ToolCall(String name, Map<String, Object> arguments) {
        public ToolCall { arguments = Map.copyOf(arguments); }
    }
    record ModelResponse(String answer, List<String> citations, List<ToolCall> toolCalls) {
        public ModelResponse { citations = List.copyOf(citations); toolCalls = List.copyOf(toolCalls); }
        public static ModelResponse answer(String value) { return new ModelResponse(value, List.of(), List.of()); }
    }
    class ModelFailure extends RuntimeException {
        public ModelFailure(String message) { super(message); }
        public ModelFailure(String message, Throwable cause) { super(message, cause); }
    }
    final class ModelUnavailable extends ModelFailure {
        public ModelUnavailable(String message) { super(message); }
    }
}
