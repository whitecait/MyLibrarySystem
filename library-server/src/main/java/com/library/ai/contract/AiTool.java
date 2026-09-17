package com.library.ai.contract;

import java.util.Set;

/** 模型可见工具定义；handlerKey 只指向批准的查询或预填入口。 */
public record AiTool(String name, String requiredCapability, String inputSchema,
                     String handlerKey, String level, Set<String> roles) {
    public AiTool {
        roles = Set.copyOf(roles);
    }
}
