---- MODULE BusinessDetail ----
(***************************************************************************)
(* AT-0026 retained read-only detail: exact reader/type/string-ID matching, *)
(* parent-owned notice facts, explicit missing-field projection, and stale  *)
(* route/session response rejection. Status timelines are out of scope.     *)
(* Business facts and notice links are immutable external projection input.  *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  Readers, SessionIds, SessionGenerations, RouteGenerations, TransactionIds,
  NoField, NoTransaction, NoReader, NoKind, NoStatus, NoTitle, NoBusinessDate,
  NoDetailValue, UnknownGuaranteeDisplay, UnknownTextDisplay, UnboundCopyDisplay,
  MaxSessionGeneration, MaxRouteGeneration

ReaderA == "reader-a"
ReaderB == "reader-b"
SessionA == "session-a"
SessionB == "session-b"
BigTransactionId == "9007199254740993"
BorrowKinds == {"RESERVATION", "LOAN"}
OtherKinds == {"FINE", "ORDER"}
BusinessKinds == BorrowKinds \cup OtherKinds

Row(owner, kind, id, status, title, businessDate, guaranteeType,
    reservationDate, pickupSlot, copyId) ==
  [owner |-> owner, kind |-> kind, id |-> id, status |-> status,
   title |-> title, businessDate |-> businessDate, guaranteeType |-> guaranteeType,
   reservationDate |-> reservationDate, pickupSlot |-> pickupSlot, copyId |-> copyId]

B2 == Row(ReaderA, "RESERVATION", BigTransactionId, "CONFIRMED", "title-b2",
          "opaque-business-date-b2", "DIRECT_CONFIRMATION", "opaque-date-b2",
          "opaque-slot-b2", "copy-b2")
B3 == Row(ReaderA, "RESERVATION", "tx-b3", "CONFIRMED", "title-b3",
          "opaque-business-date-b3", "WAITLIST", "opaque-date-b3",
          "opaque-slot-b3", "copy-b3")
B5 == Row(ReaderA, "LOAN", "tx-b5", "NOT_BORROWED", "title-b5",
          "opaque-business-date-b5", NoField, NoField, NoField, NoField)
LegacyReservation == Row(ReaderA, "RESERVATION", "tx-legacy", "CONFIRMED", "title-legacy",
                         "opaque-business-date-legacy", NoField, "opaque-date-legacy",
                         "PM", "copy-legacy")
ReaderBReservation == Row(ReaderB, "RESERVATION", BigTransactionId, "CONFIRMED", "reader-b-title",
                          "opaque-business-date-reader-b", "DIRECT_CONFIRMATION",
                          "opaque-date-reader-b", "opaque-slot-reader-b", "copy-reader-b")
ForeignLoan == Row(ReaderB, "LOAN", "tx-foreign", "LENT", "foreign-title",
                   "opaque-business-date-foreign", NoField, NoField, NoField, NoField)
SameIdFine == Row(ReaderA, "FINE", BigTransactionId, "OPEN", "fine-title",
                  NoField, NoField, NoField, NoField, NoField)
SameIdOrder == Row(ReaderA, "ORDER", BigTransactionId, "OPEN", "order-title",
                   NoField, NoField, NoField, NoField, NoField)
OtherFine == Row(ReaderA, "FINE", "tx-fine", "OPEN", "fine-only-title",
                 NoField, NoField, NoField, NoField, NoField)
OtherOrder == Row(ReaderA, "ORDER", "tx-order", "OPEN", "order-only-title",
                  NoField, NoField, NoField, NoField, NoField)

BusinessFacts == {B2, B3, B5, LegacyReservation, ReaderBReservation, ForeignLoan,
                  SameIdFine, SameIdOrder, OtherFine, OtherOrder}

N_B2 == <<ReaderA, "RESERVATION", BigTransactionId, "notice-b2", "VISIT",
         "opaque-notice-date-b2", FALSE>>
N_B5 == <<ReaderA, "LOAN", "tx-b5", "notice-b5", "NO_VISIT",
         "opaque-notice-date-b5", TRUE>>
N_FineSameId == <<ReaderA, "FINE", BigTransactionId, "notice-fine", "opaque-fine-type",
                 "opaque-fine-date", FALSE>>
N_OrderSameId == <<ReaderA, "ORDER", BigTransactionId, "notice-order", "opaque-order-type",
                  "opaque-order-date", FALSE>>
NotificationFacts == {N_B2, N_B5, N_FineSameId, N_OrderSameId}

NoBusinessRow == Row(NoReader, NoKind, NoTransaction, NoStatus, NoTitle,
                      NoBusinessDate, NoField, NoField, NoField, NoField)

SnapshotRows(reader) == {row \in BusinessFacts : row.owner = reader}

DetailRowsFor(reader, txId) ==
  {row \in SnapshotRows(reader) : row.kind \in BorrowKinds /\ row.id = txId}

NotificationsFor(row) ==
  {notice \in NotificationFacts :
      notice[1] = row.owner /\ notice[2] = row.kind /\ notice[3] = row.id}

DetailFields(row) ==
  [guaranteeType |->
       IF row.guaranteeType = NoField THEN UnknownGuaranteeDisplay ELSE row.guaranteeType,
   reservationDate |->
       IF row.reservationDate = NoField THEN UnknownTextDisplay ELSE row.reservationDate,
   pickupSlot |->
       IF row.pickupSlot = NoField THEN UnknownTextDisplay ELSE row.pickupSlot,
   copyId |-> IF row.copyId = NoField THEN UnboundCopyDisplay ELSE row.copyId]

NoDetailFields ==
  [guaranteeType |-> NoDetailValue, reservationDate |-> NoDetailValue,
   pickupSlot |-> NoDetailValue, copyId |-> NoDetailValue]

SnapshotRowSpace ==
  [owner : Readers, kind : BusinessKinds, id : TransactionIds,
   status : {"CONFIRMED", "WAITLIST", "RETURNED", "RETURN_ABNORMAL", "NOT_BORROWED", "LENT", "OPEN"},
   title : {"title-b2", "title-b3", "title-b5", "title-legacy", "reader-b-title",
            "foreign-title", "fine-title", "order-title", "fine-only-title", "order-only-title", NoTitle},
   businessDate : {"opaque-business-date-b2", "opaque-business-date-b3", "opaque-business-date-b5",
                   "opaque-business-date-legacy", "opaque-business-date-reader-b",
                   "opaque-business-date-foreign", NoField, NoBusinessDate},
   guaranteeType : {"DIRECT_CONFIRMATION", "WAITLIST", NoField},
   reservationDate : {"opaque-date-b2", "opaque-date-b3", "opaque-date-legacy", "opaque-date-reader-b",
                      NoField},
   pickupSlot : {"opaque-slot-b2", "opaque-slot-b3", "opaque-slot-reader-b", "PM", NoField},
   copyId : {"copy-b2", "copy-b3", "copy-legacy", "copy-reader-b", NoField}]

DetailRequestSpace ==
  [session : SessionIds, reader : Readers, sessionGeneration : SessionGenerations,
   routeGeneration : RouteGenerations, txId : TransactionIds]

NoDetailRequest ==
  [session |-> SessionA, reader |-> ReaderA, sessionGeneration |-> 0,
   routeGeneration |-> 0, txId |-> NoTransaction]

SessionReader(session) == IF session = SessionB THEN ReaderB ELSE ReaderA

EventTags == {
  "NewActiveDetailRendered", "StaleRouteDetailDiscarded", "StaleAccountDetailDiscarded",
  "OldRouteDetailAfterNewRendered", "OldAccountDetailAfterNewRendered",
  "ExactStringIdPreserved", "BorrowRowWonSameIdCollision",
  "B2NoticeAttached", "B5NoticeAttached", "UnknownFieldsExplicit",
  "ForeignDetailRejected", "MissingDetailRejected"
}

VARIABLES
  activeSession, sessionGeneration, routeTransaction, routeSession, routeGeneration,
  oldDetailRequest, currentDetailRequest,
  detailRecord, detailNotifications, detailFields, tags

vars == <<activeSession, sessionGeneration, routeTransaction, routeSession, routeGeneration,
          oldDetailRequest, currentDetailRequest,
          detailRecord, detailNotifications, detailFields, tags>>

Init ==
  /\ activeSession = SessionA
  /\ sessionGeneration = 0
  /\ routeTransaction = NoTransaction
  /\ routeSession = SessionA
  /\ routeGeneration = 0
  /\ oldDetailRequest = NoDetailRequest
  /\ currentDetailRequest = NoDetailRequest
  /\ detailRecord = NoBusinessRow
  /\ detailNotifications = {}
  /\ detailFields = NoDetailFields
  /\ tags = {}

OpenDetail(txId) ==
  /\ txId \in TransactionIds
  /\ routeGeneration < MaxRouteGeneration
  /\ (currentDetailRequest = NoDetailRequest \/ oldDetailRequest = NoDetailRequest)
  /\ routeTransaction' = txId
  /\ routeSession' = activeSession
  /\ routeGeneration' = routeGeneration + 1
  /\ IF currentDetailRequest = NoDetailRequest
       THEN /\ oldDetailRequest' = oldDetailRequest
            /\ currentDetailRequest' = NoDetailRequest
       ELSE /\ oldDetailRequest' = currentDetailRequest
            /\ currentDetailRequest' = NoDetailRequest
  /\ detailRecord' = NoBusinessRow
  /\ detailNotifications' = {}
  /\ detailFields' = NoDetailFields
  /\ UNCHANGED <<activeSession, sessionGeneration, tags>>

SwitchSession(session) ==
  /\ session \in SessionIds
  /\ session # activeSession
  /\ sessionGeneration < MaxSessionGeneration
  /\ routeGeneration < MaxRouteGeneration
  /\ (currentDetailRequest = NoDetailRequest \/ oldDetailRequest = NoDetailRequest)
  /\ activeSession' = session
  /\ sessionGeneration' = sessionGeneration + 1
  /\ routeSession' = session
  /\ routeGeneration' = routeGeneration + 1
  /\ IF currentDetailRequest = NoDetailRequest
       THEN /\ oldDetailRequest' = oldDetailRequest
            /\ currentDetailRequest' = NoDetailRequest
       ELSE /\ oldDetailRequest' = currentDetailRequest
            /\ currentDetailRequest' = NoDetailRequest
  /\ detailRecord' = NoBusinessRow
  /\ detailNotifications' = {}
  /\ detailFields' = NoDetailFields
  /\ UNCHANGED <<routeTransaction, tags>>

StartDetailRequest ==
  /\ routeTransaction \in TransactionIds
  /\ routeSession = activeSession
  /\ currentDetailRequest = NoDetailRequest
  /\ currentDetailRequest' =
       [session |-> activeSession, reader |-> SessionReader(activeSession),
        sessionGeneration |-> sessionGeneration, routeGeneration |-> routeGeneration,
        txId |-> routeTransaction]
  /\ UNCHANGED <<activeSession, sessionGeneration, routeTransaction, routeSession,
                 routeGeneration, oldDetailRequest, detailRecord,
                 detailNotifications, detailFields, tags>>

CompleteDetail(request, slot) ==
  LET rows == DetailRowsFor(request.reader, request.txId)
      result == IF rows = {} THEN NoBusinessRow ELSE CHOOSE row \in rows : TRUE
      resultNotifications == IF result = NoBusinessRow THEN {} ELSE NotificationsFor(result)
      resultFields == IF result = NoBusinessRow THEN NoDetailFields ELSE DetailFields(result)
      current == request.session = activeSession
                  /\ request # NoDetailRequest
                  /\ request.reader = SessionReader(activeSession)
                  /\ request.sessionGeneration = sessionGeneration
                  /\ request.session = routeSession
                  /\ request.routeGeneration = routeGeneration
                  /\ request.txId = routeTransaction
      eventTags ==
        IF ~current
          THEN IF request.session # activeSession
                    \/ request.reader # SessionReader(activeSession)
                    \/ request.sessionGeneration # sessionGeneration
                 THEN {"StaleAccountDetailDiscarded"}
                      \cup (IF slot = "OLD" /\ "NewActiveDetailRendered" \in tags
                              THEN {"OldAccountDetailAfterNewRendered"} ELSE {})
                 ELSE {"StaleRouteDetailDiscarded"}
                      \cup (IF slot = "OLD" /\ "NewActiveDetailRendered" \in tags
                              THEN {"OldRouteDetailAfterNewRendered"} ELSE {})
          ELSE (IF result # NoBusinessRow /\ slot = "CURRENT"
                  THEN {"NewActiveDetailRendered"} ELSE {})
               \cup (IF result = B2 /\ resultNotifications = {N_B2}
                       THEN {"B2NoticeAttached"} ELSE {})
               \cup (IF result = B5 /\ resultNotifications = {N_B5}
                       THEN {"B5NoticeAttached"} ELSE {})
               \cup (IF result = B2 /\ \E other \in BusinessFacts :
                         other.kind \in OtherKinds /\ other.owner = request.reader
                           /\ other.id = request.txId
                       THEN {"BorrowRowWonSameIdCollision"} ELSE {})
               \cup (IF result = B2 /\ result.id = BigTransactionId
                       THEN {"ExactStringIdPreserved"} ELSE {})
               \cup (IF result = B5 /\ result.guaranteeType = NoField
                             /\ result.pickupSlot = NoField /\ result.copyId = NoField
                             /\ resultFields = DetailFields(result)
                       THEN {"UnknownFieldsExplicit"} ELSE {})
               \cup (IF result = NoBusinessRow /\ \E row \in BusinessFacts :
                         row.owner # request.reader /\ row.kind \in BorrowKinds
                           /\ row.id = request.txId
                       THEN {"ForeignDetailRejected"} ELSE {})
               \cup (IF result = NoBusinessRow /\ \A row \in BusinessFacts : row.id # request.txId
                       THEN {"MissingDetailRejected"} ELSE {})
  IN
    /\ request # NoDetailRequest
    /\ IF slot = "OLD"
         THEN /\ oldDetailRequest = request
              /\ oldDetailRequest' = NoDetailRequest
              /\ currentDetailRequest' = currentDetailRequest
         ELSE /\ currentDetailRequest = request
              /\ currentDetailRequest' = NoDetailRequest
              /\ oldDetailRequest' = oldDetailRequest
    /\ IF current
         THEN
           /\ detailRecord' = result
           /\ detailNotifications' = resultNotifications
           /\ detailFields' = resultFields
           /\ tags' = tags \cup eventTags
         ELSE
           /\ UNCHANGED <<detailRecord, detailNotifications, detailFields>>
           /\ tags' = tags \cup eventTags
    /\ UNCHANGED <<activeSession, sessionGeneration, routeTransaction, routeSession, routeGeneration>>

CompleteCurrentDetail == CompleteDetail(currentDetailRequest, "CURRENT")
CompleteOldDetail == CompleteDetail(oldDetailRequest, "OLD")

Next ==
  \/ \E txId \in TransactionIds : OpenDetail(txId)
  \/ \E session \in SessionIds : SwitchSession(session)
  \/ StartDetailRequest
  \/ CompleteCurrentDetail
  \/ CompleteOldDetail

TypeOK ==
  /\ activeSession \in SessionIds
  /\ sessionGeneration \in SessionGenerations
  /\ routeTransaction \in TransactionIds \cup {NoTransaction}
  /\ routeSession \in SessionIds
  /\ routeGeneration \in RouteGenerations
  /\ (oldDetailRequest = NoDetailRequest \/ oldDetailRequest \in DetailRequestSpace)
  /\ (currentDetailRequest = NoDetailRequest \/ currentDetailRequest \in DetailRequestSpace)
  /\ detailRecord \in SnapshotRowSpace \cup {NoBusinessRow}
  /\ detailNotifications \subseteq NotificationFacts
  /\ tags \subseteq EventTags

InvDetailIsCurrentReaderBorrowRow ==
  detailRecord = NoBusinessRow \/
    /\ detailRecord \in BusinessFacts
    /\ detailRecord.owner = SessionReader(activeSession)
    /\ detailRecord.kind \in BorrowKinds
    /\ detailRecord.id = routeTransaction
    /\ routeSession = activeSession

InvDetailNotificationsMatchParent ==
  IF detailRecord = NoBusinessRow
    THEN detailNotifications = {}
    ELSE detailNotifications = NotificationsFor(detailRecord)

InvDetailFieldsFaithful ==
  IF detailRecord = NoBusinessRow
    THEN detailFields = NoDetailFields
    ELSE detailFields = DetailFields(detailRecord)

InvUnknownFieldsExplicit ==
  /\ (detailRecord = B5 => detailFields.guaranteeType = UnknownGuaranteeDisplay)
  /\ (detailRecord = B5 => detailFields.reservationDate = UnknownTextDisplay)
  /\ (detailRecord = B5 => detailFields.pickupSlot = UnknownTextDisplay)
  /\ (detailRecord = B5 => detailFields.copyId = UnboundCopyDisplay)
  /\ (detailRecord = LegacyReservation => detailFields.guaranteeType = UnknownGuaranteeDisplay)

ProbeNotExactStringIdPreserved == "ExactStringIdPreserved" \notin tags
ProbeNotBorrowRowWonSameIdCollision == "BorrowRowWonSameIdCollision" \notin tags
ProbeNotB2B5NoticeAttached == ~({"B2NoticeAttached", "B5NoticeAttached"} \subseteq tags)
ProbeNotUnknownFieldsExplicit == "UnknownFieldsExplicit" \notin tags
ProbeNotForeignDetailRejected == "ForeignDetailRejected" \notin tags
ProbeNotMissingDetailRejected == "MissingDetailRejected" \notin tags
ProbeNotStaleRouteDetailDiscarded == "StaleRouteDetailDiscarded" \notin tags
ProbeNotStaleAccountDetailDiscarded == "StaleAccountDetailDiscarded" \notin tags
ProbeNotOldRouteDetailAfterNewRendered == "OldRouteDetailAfterNewRendered" \notin tags
ProbeNotOldAccountDetailAfterNewRendered == "OldAccountDetailAfterNewRendered" \notin tags

=============================================================================
