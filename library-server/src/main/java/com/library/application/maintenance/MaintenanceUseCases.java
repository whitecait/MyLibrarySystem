package com.library.application.maintenance;

import com.library.catalog.contract.MaintainTitle;
import com.library.catalog.contract.ReadingContent;
import com.library.catalog.contract.ReadingContentQuery;
import com.library.catalog.contract.TitleChange;
import com.library.catalog.contract.TitleCommands;
import com.library.catalog.contract.TitleCriteria;
import com.library.catalog.contract.TitlePage;
import com.library.catalog.contract.TitleQuery;
import com.library.catalog.contract.TitleView;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.CopyCommands;
import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.AuthorizationDecision;
import com.library.contract.security.Capability;
import com.library.contract.security.SubjectRef;
import com.library.contract.security.Visibility;
import com.library.identity.contract.AuthorizationCheck;
import org.springframework.stereotype.Component;

/**
 * 书目与副本维护应用用例（MLS-DOC-MD-0004 §3）：
 * 书目经 CATALOG TitleCommands；副本维护经 CATALOG→CIRCULATION 的 CopyCommands
 * （MD-0002 §3：CATALOG 不拥有副本状态，维护副本时调用 CIRCULATION）。
 * 公开读操作不做授权；维护操作要求 LIBRARIAN。
 */
public interface MaintenanceUseCases {

    ModuleResult<TitlePage> search(String q, String category, String availability, PageCursor page);

    ModuleResult<TitleView> title(long titleId);

    ModuleResult<ReadingContent> readingContent(long titleId, PageCursor page);

    ModuleResult<TitleChange> createTitle(ActorContext actor, MaintainTitle command);

    ModuleResult<TitleChange> updateTitle(ActorContext actor, MaintainTitle command);

    ModuleResult<TitleChange> removeTitle(ActorContext actor, MaintainTitle command);

    ModuleResult<CopyCommands.CopySnapshot> createCopy(ActorContext actor, CopyCommands.CreateCopy command);

    ModuleResult<CopyCommands.CopyPage> listCopies(ActorContext actor, CopyCommands.CopyCriteria criteria);

    ModuleResult<CopyCommands.CopySnapshot> changeCopyState(ActorContext actor, CopyCommands.MaintainCopy command);

    /** 公开预约日历（无授权；日期守卫在模块）。 */
    ModuleResult<com.library.circulation.contract.AvailabilityQuery.ReservationCalendar> reservationCalendar(
            TitleId titleId, com.library.circulation.contract.model.BusinessDate from,
            com.library.circulation.contract.model.BusinessDate to);
}

@Component
record MaintenanceUseCasesImpl(TitleQuery titleQuery,
                               TitleCommands titleCommands,
                               ReadingContentQuery readingContentQuery,
                               CopyCommands copyCommands,
                               com.library.circulation.contract.AvailabilityQuery availabilityQuery,
                               AuthorizationCheck authorization) implements MaintenanceUseCases {

    @Override
    public ModuleResult<TitlePage> search(String q, String category, String availability, PageCursor page) {
        return titleQuery.search(new TitleCriteria(q, category, availability, page), Visibility.PUBLIC);
    }

    @Override
    public ModuleResult<TitleView> title(long titleId) {
        return titleQuery.title(TitleId.of(titleId), Visibility.PUBLIC);
    }

    @Override
    public ModuleResult<ReadingContent> readingContent(long titleId, PageCursor page) {
        return readingContentQuery.readingContent(TitleId.of(titleId), page);
    }

    @Override
    public ModuleResult<TitleChange> createTitle(ActorContext actor, MaintainTitle command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return titleCommands.maintain(command);
    }

    @Override
    public ModuleResult<TitleChange> updateTitle(ActorContext actor, MaintainTitle command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return titleCommands.maintain(command);
    }

    @Override
    public ModuleResult<TitleChange> removeTitle(ActorContext actor, MaintainTitle command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return titleCommands.maintain(command);
    }

    @Override
    public ModuleResult<CopyCommands.CopySnapshot> createCopy(ActorContext actor,
                                                              CopyCommands.CreateCopy command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return copyCommands.createCopy(command);
    }

    @Override
    public ModuleResult<CopyCommands.CopyPage> listCopies(ActorContext actor,
                                                          CopyCommands.CopyCriteria criteria) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return copyCommands.listCopies(criteria);
    }

    @Override
    public ModuleResult<CopyCommands.CopySnapshot> changeCopyState(ActorContext actor,
                                                                   CopyCommands.MaintainCopy command) {
        var rejected = authorizeOrReject(actor);
        if (rejected != null) {
            return ModuleResult.Rejected.propagate(rejected);
        }
        return copyCommands.maintainCopy(command);
    }

    @Override
    public ModuleResult<com.library.circulation.contract.AvailabilityQuery.ReservationCalendar> reservationCalendar(
            TitleId titleId, com.library.circulation.contract.model.BusinessDate from,
            com.library.circulation.contract.model.BusinessDate to) {
        return availabilityQuery.calendar(titleId, from, to);
    }

    private ModuleResult.Rejected<AuthorizationDecision> authorizeOrReject(ActorContext actor) {
        if (authorization.authorize(actor, Capability.LIBRARIAN, SubjectRef.system())
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }
}
