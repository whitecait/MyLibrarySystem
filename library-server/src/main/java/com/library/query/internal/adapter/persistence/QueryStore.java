package com.library.query.internal.adapter.persistence;

import java.time.LocalDate;
import java.util.List;

/**
 * QUERY 持久化内部缝：只读组合快照（MLS-DOC-MD-0002 §3——QUERY 不维护权威状态）。
 * 直接读取各模块表做只读投影，不产生任何写访问。
 */
public interface QueryStore {

    /** 四项固定口径（FR-0039）：expected/arrived/pending/exceptions。 */
    ArrivalCounts arrivals(LocalDate date);

    List<MyBusinessRow> myBusiness(long readerId, List<String> kinds, String status,
                                   long beforeId, int limit);

    record ArrivalCounts(int expected, int arrived, int pending, int exceptions) {
    }

    /** kind ∈ RESERVATION|LOAN|FINE（B2；ORDER 随 B3 订单事实加入）。 */
    record MyBusinessRow(String kind, long id, String status, String title,
                         LocalDate businessDate, Long amountCents) {
    }
}
