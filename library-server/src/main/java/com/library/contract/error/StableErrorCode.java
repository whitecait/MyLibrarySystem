package com.library.contract.error;

import java.util.Locale;

/**
 * MD-0003 已登记稳定错误码与 HTTP 状态的唯一定位点。
 * 每个码的状态取值以该码登记条目与共同样例为准；随批次实现的操作逐一在此登记。
 * 业务模块只能通过 {@link ModuleResult.Rejected} 的 code 字符串引用这些值。
 */
public enum StableErrorCode {

    // IDENTITY
    MLS_ID_001("MLS-ID-001", 401), // 未认证/需登录
    MLS_ID_002("MLS-ID-002", 409), // 用户名重名
    MLS_ID_003("MLS-ID-003", 400), // 注册字段无效
    MLS_ID_004("MLS-ID-004", 401), // 登录凭据失败
    MLS_ID_005("MLS-ID-005", 403), // 账户不可用
    MLS_ID_006("MLS-ID-006", 409), // 账户状态冲突

    // CATALOG
    MLS_CAT_001("MLS-CAT-001", 400), // 查询无效
    MLS_CAT_002("MLS-CAT-002", 404), // 书目不存在
    MLS_CAT_003("MLS-CAT-003", 409), // ISBN/版本冲突
    MLS_CAT_004("MLS-CAT-004", 409), // 删除仍有受约束事实
    MLS_CAT_005("MLS-CAT-005", 404), // 无公开内容

    // CIRCULATION
    MLS_CIRC_001("MLS-CIRC-001", 400), // 查询/日期无效
    MLS_CIRC_002("MLS-CIRC-002", 409), // 闭馆隔离
    MLS_CIRC_003("MLS-CIRC-003", 409), // 状态冲突/非法迁移/终态
    MLS_CIRC_004("MLS-CIRC-004", 404), // 无效取书对象
    MLS_CIRC_005("MLS-CIRC-005", 422), // 人工处置守卫不满足
    MLS_CIRC_006("MLS-CIRC-006", 409), // 重复归还
    MLS_CIRC_007("MLS-CIRC-007", 422), // 归还入口转人工
    MLS_CIRC_008("MLS-CIRC-008", 409), // 重开未追平/无权
    MLS_CIRC_010("MLS-CIRC-010", 409), // 重复副本
    MLS_CIRC_011("MLS-CIRC-011", 422), // 预约资格拒绝
    MLS_CIRC_012("MLS-CIRC-012", 422), // 续借拒绝
    MLS_CIRC_013("MLS-CIRC-013", 409), // 闭馆已运行
    MLS_CIRC_014("MLS-CIRC-014", 409), // 闭馆协调失败

    // SETTLEMENT
    MLS_PAY_001("MLS-PAY-001", 409), // 罚款已被订单占用（共同样例 ORDER-CONCURRENT-CONFLICT = 409）
    MLS_PAY_002("MLS-PAY-002", 409), // 罚款已结清
    MLS_PAY_003("MLS-PAY-003", 409), // 回调冲突事件
    MLS_PAY_004("MLS-PAY-004", 422), // 关闭提交失败
    MLS_PAY_005("MLS-PAY-005", 404), // 结算对象不可见
    MLS_PAY_006("MLS-PAY-006", 403), // 非本地模拟器
    MLS_PAY_007("MLS-PAY-007", 422), // 退款金额/终态不合法
    MLS_PAY_008("MLS-PAY-008", 409), // 对账日期重复/冲突

    // REVIEW
    MLS_REV_001("MLS-REV-001", 409), // 目标已变（共同样例 REVIEW-TARGET-CHANGED = 409）
    MLS_REV_002("MLS-REV-002", 422), // 目标执行失败
    MLS_REV_003("MLS-REV-003", 422), // 不属高风险动作

    // QUERY / DELIVERY
    MLS_QUERY_001("MLS-QUERY-001", 400), // 日期无效
    MLS_DEL_001("MLS-DEL-001", 404), // 通知不可见

    // AI
    MLS_AI_001("MLS-AI-001", 500), // AI 不可用
    MLS_AI_002("MLS-AI-002", 429), // 额度耗尽（共同样例 AI-QUOTA-EXHAUSTED = 429）
    MLS_AI_003("MLS-AI-003", 500), // 模型端点失败
    MLS_AI_004("MLS-AI-004", 422), // 工具拒绝
    MLS_AI_005("MLS-AI-005", 404), // 会话不可见

    // IDEMPOTENCY
    MLS_IDEMP_002("MLS-IDEMP-002", 409), // 同键不同摘要

    /**
     * 基础设施失败的 HTTP 500 信封码。MD-0003/0004 未为系统失败登记业务稳定码
     * （业务拒绝不适用），OpenAPI 仅约束码型；该值只由 HTTP Adapter 的异常映射产生，
     * 不作为业务拒绝码使用。
     */
    MLS_SYS_001("MLS-SYS-001", 500);

    private final String code;
    private final int httpStatus;

    StableErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static StableErrorCode of(String code) {
        for (StableErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未登记的稳定错误码: " + code);
    }

    public static int httpStatusOf(String code) {
        try {
            return of(code).httpStatus();
        } catch (IllegalArgumentException unknown) {
            return 500;
        }
    }
}
