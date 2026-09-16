package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.contract.security.Visibility;
import com.library.circulation.contract.model.CopyId;
import com.library.catalog.contract.model.TitleId;

import java.util.List;

/**
 * CIRCULATION 副本维护能力（MLS-DOC-MD-0004 §2；书目维护用例经 CATALOG→CIRCULATION 调用）。
 * action ∈ SHELVE|MAINTAIN|RESTORE|REMOVE；非法迁移/版本冲突 MLS-CIRC-003(409)；
 * 重复副本 MLS-CIRC-010(409)；已下架不可逆（BR-0044）。
 */
public interface CopyCommands {

    ModuleResult<CopySnapshot> createCopy(CreateCopy command);

    ModuleResult<CopyPage> listCopies(CopyCriteria criteria);

    ModuleResult<CopySnapshot> maintainCopy(MaintainCopy command);

    record CreateCopy(TitleId titleId, String copyNo, ActorContext actor) {
    }

    record CopyCriteria(TitleId titleId, String status, PageCursor page) {
    }

    record MaintainCopy(CopyId copyId, String action, long version, ActorContext actor) {
    }

    /** 副本快照（OpenAPI CopyData：status 为 API 六态，SHELF_READY 映射 AVAILABLE）。 */
    record CopySnapshot(CopyId copyId, TitleId titleId, String copyNo, String status, long version) {
    }

    record CopyPage(List<CopySnapshot> items, String nextCursor) {
    }
}
