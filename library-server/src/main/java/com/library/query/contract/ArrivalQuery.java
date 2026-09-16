package com.library.query.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.BusinessDate;

import java.util.List;

/**
 * QUERY 首页到馆统计能力（GET /dashboard/arrivals 的承载；MLS-DOC-MD-0004 §2）。
 * 四项固定口径（FR-0039）：expected=当日应到馆预约数；arrived=当日已借出数；
 * pending=当日已确认未取书数；exceptions=当日人工处置中数。
 */
public interface ArrivalQuery {

    ModuleResult<ArrivalDashboard> arrivals(BusinessDate date, ActorContext actor);

    record ArrivalDashboard(BusinessDate businessDate, int expected, int arrived,
                            int pending, int exceptions) {
    }
}
