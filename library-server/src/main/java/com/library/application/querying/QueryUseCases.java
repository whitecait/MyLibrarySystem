package com.library.application.querying;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.circulation.contract.model.BusinessDate;
import com.library.identity.contract.AuthorizationCheck;
import com.library.query.contract.ArrivalQuery;
import com.library.query.contract.MyBusinessQuery;
import org.springframework.stereotype.Component;

/**
 * 查询应用用例（MLS-DOC-MD-0004 §3）：门户、我的业务、首页统计。
 * 只读，不以缓存结果判定提交；到馆统计要求 LIBRARIAN，我的业务限定本人（READER）。
 */
public interface QueryUseCases {

    ModuleResult<ArrivalQuery.ArrivalDashboard> arrivals(ActorContext actor, BusinessDate date);

    ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                            String status, PageCursor page);
}

@Component
record QueryUseCasesImpl(ArrivalQuery arrivalsQuery,
                         MyBusinessQuery myBusinessQuery,
                         AuthorizationCheck authorization) implements QueryUseCases {

    @Override
    public ModuleResult<ArrivalQuery.ArrivalDashboard> arrivals(ActorContext actor, BusinessDate date) {
        if (authorization.authorize(actor, Capability.LIBRARIAN, SubjectRef.system())
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return arrivalsQuery.arrivals(date, actor);
    }

    @Override
    public ModuleResult<MyBusinessQuery.MyBusinessPage> myBusiness(ActorContext actor, String kind,
                                                                   String status, PageCursor page) {
        if (authorization.authorize(actor, Capability.READER,
                SubjectRef.account(actor.accountId().asString()))
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return myBusinessQuery.myBusiness(new MyBusinessQuery.MyBusinessCriteria(kind, status, page), actor);
    }
}
