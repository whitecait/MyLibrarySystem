package com.library.identity.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;

/**
 * IDENTITY 授权判断能力接口（MLS-DOC-MD-0004 §2）。
 * 角色不足 → MLS-ID-005(403)；会话缺失/无效 → 由 ActorResolver 先行拒绝 MLS-ID-001(401)。
 */
public interface AuthorizationCheck {

    ModuleResult<AuthorizationDecision> authorize(ActorContext actor, Capability capability, SubjectRef subject);
}
