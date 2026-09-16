package com.library.web.support;

import java.util.Map;

/**
 * 操作成功稳定码目录（operationId → MLS-&lt;MOD&gt;-S###）。
 * 共同样例中出现的码为权威值；其余操作按模块内登记顺序编号，
 * 随批次实现逐操作登记。OpenAPI 对成功 code 只约束为字符串，
 * 本目录是后端单一登记点，前端不依赖未出现在样例中的具体值。
 */
public final class SuccessCodes {

    private static final Map<String, String> BY_OPERATION = Map.ofEntries(
            // B0（共同样例 AUTH-REGISTER-SUCCESS 权威值）
            Map.entry("registerReader", "MLS-ID-S001"),
            // B1 Identity
            Map.entry("createSession", "MLS-ID-S002"),
            Map.entry("endCurrentSession", "MLS-ID-S003"),
            Map.entry("listAccounts", "MLS-ID-S004"),
            Map.entry("requestAccountChange", "MLS-ID-S005"),
            Map.entry("resetPassword", "MLS-ID-S006"),
            Map.entry("changeReaderStatus", "MLS-ID-S007"),
            // B1 Catalog（样例 CATALOG-PUBLIC-SEARCH 权威值）
            Map.entry("searchTitles", "MLS-CAT-S001"),
            Map.entry("createTitle", "MLS-CAT-S002"),
            Map.entry("getTitle", "MLS-CAT-S003"),
            Map.entry("updateTitle", "MLS-CAT-S004"),
            Map.entry("removeTitle", "MLS-CAT-S005"),
            Map.entry("getReadingContent", "MLS-CAT-S006"),
            // B2 Circulation
            Map.entry("createCopy", "MLS-CIRC-S001"),
            Map.entry("listCopies", "MLS-CIRC-S004"),
            Map.entry("changeCopyState", "MLS-CIRC-S005"),
            Map.entry("getReservationCalendar", "MLS-CIRC-S006"),
            Map.entry("reserveTitle", "MLS-CIRC-S001"),
            Map.entry("cancelReservation", "MLS-CIRC-S007"),
            Map.entry("createWalkInLoans", "MLS-CIRC-S002"),
            Map.entry("claimPickup", "MLS-CIRC-S008"),
            Map.entry("applyManualAction", "MLS-CIRC-S009"),
            Map.entry("renewLoan", "MLS-CIRC-S010"),
            Map.entry("returnCopy", "MLS-CIRC-S003"),
            // B2 Query
            Map.entry("getArrivalDashboard", "MLS-QUERY-S001"),
            Map.entry("getMyBusiness", "MLS-QUERY-S002")
    );

    private SuccessCodes() {
    }

    public static String of(String operationId) {
        var code = BY_OPERATION.get(operationId);
        if (code == null) {
            throw new IllegalStateException("操作尚未登记成功码: " + operationId);
        }
        return code;
    }
}
