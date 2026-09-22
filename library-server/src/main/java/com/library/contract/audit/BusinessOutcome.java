package com.library.contract.audit;

/**
 * 成功路径审计的 detail 固定结构（DEF-M6-011）：关联业务对象 id 与结果。
 * 两个键名是反查投影的契约——`GET /audit-events` 的 relatedId 过滤与 outcome 呈现
 * 由 `PostgresDeliveryStore.audits` 以 `detail->>'relatedId'` 与 `detail->>'outcome'` 读出，
 * 缺键即该行在反查结果中关联为空。
 */
public record BusinessOutcome(long relatedId, String outcome) {
}
