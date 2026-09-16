package com.library.circulation.contract;

import com.library.contract.ModuleResult;
import com.library.contract.security.ActorContext;
import com.library.circulation.contract.model.BusinessDate;
import com.library.catalog.contract.model.TitleId;
import com.library.circulation.contract.model.TxId;

/**
 * CIRCULATION 预约能力（MLS-DOC-MD-0004 §2）。
 * outcome ∈ CONFIRMED|WAITLIST_PENDING_CONFIRMATION|WAITLIST_ENQUEUED|MANUAL_HANDLING|REJECTED；
 * 待确认必须 txId=null 且无业务事实，确认请求复送完整字段+新幂等键重新判定（MD-0003 §3）。
 * 隔离 MLS-CIRC-002、状态冲突 MLS-CIRC-003、资格拒绝 MLS-CIRC-011。
 */
public interface ReservationCommands {

    ModuleResult<Reservation> reserve(ReservationCommand command);

    /** 取消：事务转 NOT_BORROWED 终态；终态/冲突 → MLS-CIRC-003(409)。 */
    ModuleResult<Transaction> cancel(CancelReservation command);

    /** 预约命令（readerId 由应用用例从认证主体解析后附加）。 */
    record ReservationCommand(long readerId,
                              TitleId titleId,
                              BusinessDate reservationDate,
                              String pickupSlot,
                              String channel,
                              boolean confirmWaitlist,
                              ActorContext actor) {
    }

    /** 预约结果（OpenAPI ReservationData：txId 可空 + outcome + reasons）。 */
    record Reservation(TxId txId, String outcome, java.util.List<String> reasons) {
    }

    record CancelReservation(TxId txId, String reason, ActorContext actor) {
    }
}
