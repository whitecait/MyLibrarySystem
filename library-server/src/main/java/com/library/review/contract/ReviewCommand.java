package com.library.review.contract;

import java.util.Map;

/**
 * 复核发起命令（OpenAPI ReviewRequest：targetType/targetId/action/payload/reason）。
 * targetType ∈ ACCOUNT_CHANGE | CIRCULATION_EXCEPTION | REFUND；
 * payload 为封闭动作的类型化负载；applicantId 与 targetVersion 由应用用例附加
 * （applicantId 来自认证主体，targetVersion 为发起时目标聚合版本快照）。
 */
public record ReviewCommand(long applicantId,
                            String targetType,
                            long targetId,
                            String action,
                            Map<String, Object> payload,
                            String reason,
                            long targetVersion) {
}
