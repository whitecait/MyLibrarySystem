package com.library.ai.contract;

/** HTTP 边界按已存所有者类型选择本次凭据，避免登录迁移访客会话。 */
public interface AiOwnerResolver {
    AiSessionOwner ownerFor(long sessionId, AccountAiOwner account, VisitorAiOwner visitor);
}
