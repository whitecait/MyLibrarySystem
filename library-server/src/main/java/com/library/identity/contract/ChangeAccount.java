package com.library.identity.contract;

import java.util.Map;

/**
 * 账户维护命令（应用用例附加 ActorContext）。
 * action 与 OpenAPI AccountChangeRequest.action 一致：SUSPEND/RESTORE/CHANGE_ROLE
 * 为高风险动作（须经复核，B1 直呼被拒 MLS-ID-006）；READER_STATUS 为馆员直办
 * （PATCH /readers/{readerId}/status 的承载）。
 */
public record ChangeAccount(com.library.contract.model.AccountId target,
                            String action,
                            Map<String, Object> payload,
                            String reason) {
}
