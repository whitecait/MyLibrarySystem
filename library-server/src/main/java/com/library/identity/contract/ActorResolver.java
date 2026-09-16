package com.library.identity.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;

/**
 * IDENTITY 会话身份解析能力（MLS-DOC-MD-0004 §2）：ActorContext 只由此产生。
 * 缺失/无效/过期/已吊销令牌 → MLS-ID-001(401)。
 */
public interface ActorResolver {

    ModuleResult<ActorContext> requireActor(SessionToken token);
}
