package com.library.ai.contract;

/** 封闭 AI 会话所有者；匿名凭据原值绝不进入该接口。 */
public sealed interface AiSessionOwner permits AccountAiOwner, VisitorAiOwner {
    String persistenceKey();
}
