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
 * 书目维护应用用例（MLS-DOC-MD-0004 §3；B1 只含书目，副本维护随 B2 加入 CopyCommands）。
 * 公开读操作不做授权；维护操作要求 LIBRARIAN。
 */
public interface MaintenanceUseCases {

    ModuleResult<TitlePage> search(String q, String category, PageCursor page);

    ModuleResult<TitleView> title(long titleId);

    ModuleResult<ReadingContent> readingContent(long titleId, PageCursor page);

    ModuleResult<TitleChange> createTitle(ActorContext actor, MaintainTitle command);

    ModuleResult<TitleChange> updateTitle(ActorContext actor, MaintainTitle command);

    ModuleResult<TitleChange> removeTitle(ActorContext actor, MaintainTitle command);
}

@Component
record MaintenanceUseCasesImpl(TitleQuery titleQuery,
                               TitleCommands titleCommands,
                               ReadingContentQuery readingContentQuery,
                               AuthorizationCheck authorization) implements MaintenanceUseCases {

    @Override
    public ModuleResult<TitlePage> search(String q, String category, PageCursor page) {
        return titleQuery.search(new TitleCriteria(q, category, page), Visibility.PUBLIC);
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

    private ModuleResult.Rejected<AuthorizationDecision> authorizeOrReject(ActorContext actor) {
        if (authorization.authorize(actor, Capability.LIBRARIAN, SubjectRef.system())
                instanceof ModuleResult.Rejected<AuthorizationDecision> rejected) {
            return rejected;
        }
        return null;
    }
}
