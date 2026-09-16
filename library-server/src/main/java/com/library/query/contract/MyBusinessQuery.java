package com.library.query.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;

import java.util.List;

/**
 * QUERY 我的业务组合查询能力（GET /me/business 的承载；MLS-DOC-MD-0004 §2）。
 * kind ∈ RESERVATION|LOAN|FINE|ORDER（B2 仅 RESERVATION/LOAN/FINE 有事实来源）。
 */
public interface MyBusinessQuery {

    ModuleResult<MyBusinessPage> myBusiness(MyBusinessCriteria criteria, ActorContext actor);

    record MyBusinessCriteria(String kind, String status, PageCursor page) {
    }

    record MyBusinessPage(List<BusinessItem> items, String nextCursor) {
    }

    /** 业务条目（OpenAPI BusinessItem：kind/id/status 必填，title/businessDate/amountCents 可选）。 */
    record BusinessItem(String kind, long id, String status, String title,
                        String businessDate, Long amountCents) {
    }
}
