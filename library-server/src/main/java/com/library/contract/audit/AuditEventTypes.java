package com.library.contract.audit;

/**
 * 审计 eventType 词表（DEF-M6-011）。命名与 Outbox 事件名对齐：同一业务操作在
 * `outbox_event` 与 `audit_event` 使用同一稳定名，使「事件 → 审计 → 责任链」可按同一
 * 字符串互查；无 Outbox 对应者（复核发起与决定、模拟支付调度）在本表登记。
 * 现存两类失败审计写入沿用 `ACCESS_DENIED`（ApiResponses / AuthenticationInterceptor）。
 *
 * 词表的受控登记位置：`docs/04-module-design/MLS-DOC-MD-0003.md` §7。
 */
public final class AuditEventTypes {

    /** 预约判定成立（含绑定成功、候补入队、转人工处理中）。 */
    public static final String RESERVATION_DECIDED = "RESERVATION_DECIDED";
    /** 预约取消。 */
    public static final String RESERVATION_CANCELLED = "RESERVATION_CANCELLED";
    /** 现场逐册借阅（成功册逐条登记）。 */
    public static final String WALKIN_LOAN_BATCH = "WALKIN_LOAN_BATCH";
    /** 到台取书借出。 */
    public static final String PICKUP_CLAIMED = "PICKUP_CLAIMED";
    /** 人工处置（人工例外留痕）。 */
    public static final String MANUAL_ACTION_APPLIED = "MANUAL_ACTION_APPLIED";
    /** 续借成功。 */
    public static final String LOAN_RENEWED = "LOAN_RENEWED";
    /** 归还形成罚款。 */
    public static final String FINE_CREATED = "FINE_CREATED";
    /** 归还成功。 */
    public static final String COPY_RETURNED = "COPY_RETURNED";
    /** 候补分配确认（闭馆结算或维护完成刷新结算）。 */
    public static final String WAITLIST_ASSIGNED = "WAITLIST_ASSIGNED";
    /** 候补到期清退。 */
    public static final String WAITLIST_EXPIRED = "WAITLIST_EXPIRED";
    /** 复核发起。 */
    public static final String REVIEW_REQUESTED = "REVIEW_REQUESTED";
    /** 复核决定。 */
    public static final String REVIEW_DECIDED = "REVIEW_DECIDED";
    /** 模拟支付调度。 */
    public static final String SIMULATION_SCHEDULED = "SIMULATION_SCHEDULED";

    private AuditEventTypes() {
    }
}
