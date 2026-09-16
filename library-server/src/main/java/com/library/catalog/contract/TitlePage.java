package com.library.catalog.contract;

import java.util.List;

/**
 * 公开书目投影（OpenAPI TitlePageData/TitleSummaryData）。
 * availability 只取 AVAILABLE|UNAVAILABLE|UNKNOWN：书目级展示，
 * 不泄露逐册状态，也不保证后续预约成功（MD-0003 §2）。
 */
public record TitlePage(List<TitleSummary> items, String nextCursor) {

    /** 书目摘要（OpenAPI TitleSummaryData）。 */
    public record TitleSummary(long titleId, String title, List<String> authors,
                               String category, String availability) {
    }
}
