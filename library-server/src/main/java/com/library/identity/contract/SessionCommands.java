package com.library.identity.contract;

import com.library.contract.ModuleResult;

/**
 * IDENTITY 会话命令能力（MD-0002「会话身份」公开接口的内部承载；
 * MD-0004 §1 允许实现补全稳定接口下的能力细分，随批次登记）。
 */
public interface SessionCommands {

    /** 登录成功后签发会话；凭据失败 MLS-ID-004(401)、账户不可用 MLS-ID-005(403)。 */
    ModuleResult<IssuedSession> issue(String username, String password);

    /** 吊销当前会话（幂等：已结束仍返回 ended=true）。 */
    ModuleResult<SessionEnded> end(SessionToken token);
}
