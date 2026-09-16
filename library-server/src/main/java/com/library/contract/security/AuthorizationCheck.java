package com.library.contract.security;

import com.library.contract.ModuleResult;

/**
 * 授权判断能力接口（IDENTITY 模块实现，MLS-DOC-MD-0004 §2）。
 */
public interface AuthorizationCheck {

    ModuleResult<AuthorizationDecision> authorize(ActorContext actor, Capability capability, SubjectRef subject);
}
