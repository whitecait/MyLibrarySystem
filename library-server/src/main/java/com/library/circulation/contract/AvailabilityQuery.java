package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.circulation.contract.model.BusinessDate;
import com.library.catalog.contract.model.TitleId;

/**
 * CIRCULATION 可用性与预约日历查询能力（MLS-DOC-MD-0004 §2）。
 */
public interface AvailabilityQuery {

    /**
     * @param availability 书目级投影：AVAILABLE|UNAVAILABLE|UNKNOWN
     * @param availableCopies 当前可借（SHELF_READY）副本数
     */
    ModuleResult<AvailabilitySnapshot> availability(TitleId titleId, BusinessDate date);

    /** 预约日历：逐业务日 GUARANTEED|NOT_GUARANTEED|UNAVAILABLE（FR-0046；日期无效 MLS-CIRC-001）。 */
    ModuleResult<ReservationCalendar> calendar(TitleId titleId, BusinessDate from, BusinessDate to);

    record AvailabilitySnapshot(TitleId titleId, String availability, int availableCopies) {
    }

    record CalendarDay(BusinessDate businessDate, String availability) {
    }

    record ReservationCalendar(java.util.List<CalendarDay> days) {
    }
}
