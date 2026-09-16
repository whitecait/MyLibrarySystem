package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.CopyId;

/**
 * CIRCULATION 归还能力。重复归还 MLS-CIRC-006(409)；
 * outcome RETURNED|RETURN_ABNORMAL；罚款形成由应用用例同事务编排。
 */
public interface ReturnCommands {

    ModuleResult<ReturnedCopy> returnCopy(ReturnCommand command);

    record ReturnCommand(CopyId copyId, String condition, ActorContext actor) {
    }

    /**
     * 归还结果。HTTP 映射只暴露 ReturnData 字段（txId/outcome，fineId 由应用用例补足）；
     * readerId/titleId/dueDate 为罚款形成的模块输出业务字段（不进 HTTP 响应）。
     */
    record ReturnedCopy(com.library.circulation.contract.model.TxId txId, String outcome,
                        com.library.circulation.contract.model.CopyId copyId,
                        long readerId, com.library.catalog.contract.model.TitleId titleId,
                        java.time.LocalDate dueDate) {
    }
}
