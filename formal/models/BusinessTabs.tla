---- MODULE BusinessTabs ----
(***************************************************************************)
(* AT-0026 retained slice: exclusive business groups, independent paging,   *)
(* stale list-response guards, and HISTORY cache invalidation after cancel.   *)
(* Page contents are a bounded API snapshot input, not SQL/HTTP proof.        *)
(* At most two responses are in flight. Full status timelines are out.       *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  Readers, SessionIds, Groups, BorrowKinds, OtherKinds,
  ReservationStatuses, LoanStatuses, HistoryStatuses,
  CursorValues, RequestGenerations, SessionGenerations,
  StartCursor, NoCursor, MaxRequestGeneration, MaxSessionGeneration

ASSUME StartCursor \in CursorValues
ASSUME MaxRequestGeneration \in Nat
ASSUME MaxSessionGeneration \in Nat

GroupsExpected == {"CURRENT_LOANS", "RESERVATIONS", "HISTORY"}
BorrowKindsExpected == {"RESERVATION", "LOAN"}
OtherKindsExpected == {"FINE", "ORDER"}
ReservationStatusesExpected == {"AWAIT", "CONFIRMED", "MANUAL_HANDLING", "WAITLIST"}
HistoryStatusesExpected == {"RETURNED", "RETURN_ABNORMAL", "NOT_BORROWED"}
SupportedStatuses == {"LENT"} \cup ReservationStatusesExpected \cup HistoryStatusesExpected

ReaderA == "reader-a"
ReaderB == "reader-b"
SessionA == "session-a"
SessionB == "session-b"

B1 == <<ReaderA, "LOAN", "tx-b1", "LENT">>
B2 == <<ReaderA, "RESERVATION", "tx-b2", "CONFIRMED">>
B3 == <<ReaderA, "RESERVATION", "tx-b3", "WAITLIST">>
B4 == <<ReaderA, "LOAN", "tx-b4", "RETURNED">>
B5 == <<ReaderA, "LOAN", "tx-b5", "NOT_BORROWED">>
B6 == <<ReaderA, "LOAN", "tx-b6", "LENT">>
B7 == <<ReaderA, "RESERVATION", "tx-cancel", "CONFIRMED">>
B7Canceled == <<ReaderA, "RESERVATION", "tx-cancel", "NOT_BORROWED">>
ForeignLoan == <<ReaderB, "LOAN", "tx-foreign", "LENT">>
CanceledReservation == <<ReaderA, "RESERVATION", "tx-canceled", "CANCELED">>
SameIdFine == <<ReaderA, "FINE", "tx-b2", "OPEN">>
SameIdOrder == <<ReaderA, "ORDER", "tx-b2", "OPEN">>
OtherFine == <<ReaderA, "FINE", "tx-fine", "OPEN">>
OtherOrder == <<ReaderA, "ORDER", "tx-order", "OPEN">>

BusinessFacts == {B1, B2, B3, B4, B5, B6, B7, ForeignLoan, CanceledReservation,
                  SameIdFine, SameIdOrder, OtherFine, OtherOrder}
BusinessFactSpace == BusinessFacts \cup {B7Canceled}
OtherRows == {row \in BusinessFacts : row[2] \in OtherKindsExpected}

SessionReader(session) == IF session = SessionB THEN ReaderB ELSE ReaderA

InGroup(row, group) ==
  IF group = "CURRENT_LOANS"
    THEN row[2] = "LOAN" /\ row[4] = "LENT"
  ELSE IF group = "RESERVATIONS"
    THEN row[2] = "RESERVATION" /\ row[4] \in ReservationStatusesExpected
  ELSE IF group = "HISTORY"
    THEN row[2] \in BorrowKindsExpected /\ row[4] \in HistoryStatusesExpected
  ELSE FALSE

SupportedBorrowFacts ==
  {row \in BusinessFactSpace : row[2] \in BorrowKindsExpected /\ row[4] \in SupportedStatuses}

MappedBorrowFacts ==
  {row \in BusinessFactSpace : \E group \in GroupsExpected : InGroup(row, group)}

ProjectedFacts(reservationCanceled) ==
  IF reservationCanceled
    THEN (BusinessFacts \ {B7}) \cup {B7Canceled}
    ELSE BusinessFacts

GroupPartitionContract ==
  /\ Groups = GroupsExpected
  /\ BorrowKinds = BorrowKindsExpected
  /\ OtherKinds = OtherKindsExpected
  /\ ReservationStatuses = ReservationStatusesExpected
  /\ LoanStatuses = {"LENT", "RETURNED", "RETURN_ABNORMAL", "NOT_BORROWED"}
  /\ HistoryStatuses = HistoryStatusesExpected
  /\ MappedBorrowFacts = SupportedBorrowFacts
  /\ \A status \in ReservationStatusesExpected :
       /\ InGroup(<<ReaderA, "RESERVATION", "mapping-probe", status>>, "RESERVATIONS")
       /\ ~InGroup(<<ReaderA, "RESERVATION", "mapping-probe", status>>, "HISTORY")
       /\ ~InGroup(<<ReaderA, "LOAN", "mapping-probe", status>>, "RESERVATIONS")
  /\ \A kind \in BorrowKindsExpected : \A status \in HistoryStatusesExpected :
       /\ InGroup(<<ReaderA, kind, "mapping-probe", status>>, "HISTORY")
       /\ ~InGroup(<<ReaderA, kind, "mapping-probe", status>>, "RESERVATIONS")
       /\ (kind = "LOAN" => ~InGroup(<<ReaderA, kind, "mapping-probe", status>>, "CURRENT_LOANS"))
  /\ InGroup(<<ReaderA, "LOAN", "mapping-probe", "LENT">>, "CURRENT_LOANS")
  /\ \A status \in LoanStatuses \ {"LENT"} :
       ~InGroup(<<ReaderA, "LOAN", "mapping-probe", status>>, "CURRENT_LOANS")
  /\ \A row \in BusinessFactSpace :
       Cardinality({group \in GroupsExpected : InGroup(row, group)}) <= 1
  /\ \A group \in GroupsExpected :
       {row \in BusinessFactSpace : row[1] = ReaderA /\ InGroup(row, group)}
         \cap OtherRows = {}
  /\ \A row \in OtherRows : \A group \in GroupsExpected : ~InGroup(row, group)

ExpectedRows(reader, group, reservationCanceled) ==
  {row \in ProjectedFacts(reservationCanceled) : row[1] = reader /\ InGroup(row, group)}

PageRows(reader, group, cursor, reservationCanceled) ==
  IF reader = ReaderA
    THEN IF group = "CURRENT_LOANS"
           THEN IF cursor = StartCursor THEN {B1} ELSE IF cursor = "loan-next" THEN {B6} ELSE {}
         ELSE IF group = "RESERVATIONS"
           THEN IF cursor = StartCursor
                  THEN IF reservationCanceled THEN {B2} ELSE {B2, B7}
                  ELSE IF cursor = "reservation-next" THEN {B3} ELSE {}
         ELSE IF group = "HISTORY"
           THEN IF cursor = StartCursor
                  THEN IF reservationCanceled THEN {B4, B7Canceled} ELSE {B4}
                ELSE IF cursor = "history-next"
                       THEN {B5}
                       ELSE {}
         ELSE {}
    ELSE IF reader = ReaderB
      THEN IF group = "CURRENT_LOANS" /\ cursor = StartCursor THEN {ForeignLoan} ELSE {}
      ELSE {}

PageNextCursor(reader, group, cursor) ==
  IF reader = ReaderA /\ cursor = StartCursor /\ group = "CURRENT_LOANS"
    THEN "loan-next"
  ELSE IF reader = ReaderA /\ cursor = StartCursor /\ group = "RESERVATIONS"
    THEN "reservation-next"
  ELSE IF reader = ReaderA /\ cursor = StartCursor /\ group = "HISTORY"
    THEN "history-next"
  ELSE NoCursor

CursorMatchesGroup(group, cursor) ==
  cursor = StartCursor \/
  (group = "CURRENT_LOANS" /\ cursor = "loan-next") \/
  (group = "RESERVATIONS" /\ cursor = "reservation-next") \/
  (group = "HISTORY" /\ cursor = "history-next")

PageContract ==
  /\ \A reader \in Readers : \A group \in GroupsExpected :
       PageRows(reader, group, StartCursor, FALSE) \cup
         PageRows(reader, group, IF group = "CURRENT_LOANS" THEN "loan-next"
                                  ELSE IF group = "RESERVATIONS" THEN "reservation-next"
                                  ELSE "history-next", FALSE) = ExpectedRows(reader, group, FALSE)
  /\ \A reader \in Readers : \A group \in GroupsExpected :
       PageRows(reader, group, StartCursor, FALSE) \cap
         PageRows(reader, group, IF group = "CURRENT_LOANS" THEN "loan-next"
                                  ELSE IF group = "RESERVATIONS" THEN "reservation-next"
                                  ELSE "history-next", FALSE) = {}
  /\ \A group \in GroupsExpected :
       PageNextCursor(ReaderA, group, StartCursor) =
         IF group = "CURRENT_LOANS" THEN "loan-next"
         ELSE IF group = "RESERVATIONS" THEN "reservation-next"
         ELSE "history-next"

AllGroupPagesApplied(rowsByGroup, reservationCanceled) ==
  /\ ExpectedRows(ReaderA, "CURRENT_LOANS", reservationCanceled) \subseteq rowsByGroup["CURRENT_LOANS"]
  /\ ExpectedRows(ReaderA, "RESERVATIONS", reservationCanceled) \subseteq rowsByGroup["RESERVATIONS"]
  /\ ExpectedRows(ReaderA, "HISTORY", reservationCanceled) \subseteq rowsByGroup["HISTORY"]

VARIABLES
  activeSession, activeGroup, sessionGeneration,
  listRows, listNextCursor, listHasLoaded, pageRequestGeneration,
  oldPageRequest, currentPageRequest, reservationCanceled, historyInvalidated, tags

vars == <<activeSession, activeGroup, sessionGeneration,
          listRows, listNextCursor, listHasLoaded, pageRequestGeneration,
          oldPageRequest, currentPageRequest, reservationCanceled, historyInvalidated, tags>>

PageRequestSpace ==
  [group : GroupsExpected, session : SessionIds, sessionGeneration : SessionGenerations,
   requestGeneration : RequestGenerations, cursor : CursorValues,
   reservationCanceledAtStart : BOOLEAN]

NoPageRequest ==
  [group |-> "CURRENT_LOANS", session |-> SessionA, sessionGeneration |-> 0,
   requestGeneration |-> 0, cursor |-> StartCursor,
   reservationCanceledAtStart |-> FALSE]

EventTags == {
  "GroupPartitionChecked", "OtherKindsExcluded", "IndependentPaginationCompleted",
  "InactiveTabResponseIsolated", "NewActivePageRendered",
  "StaleAccountPageDiscarded", "OldAccountResponseAfterNewRendered",
  "StalePageGenerationDiscarded", "OldGenerationResponseAfterNewRendered",
  "OldTabResponseAfterNewRendered", "HistoryCacheInvalidatedOnCancel",
  "ReservationCacheInvalidatedOnCancel", "CanceledReservationRefetchedInHistory",
  "ReservationCacheRefreshedAfterCancel",
  "ActiveHistoryRefreshedAfterCancel", "ActiveReservationRefreshedAfterCancel",
  "OldHistoryResponseAfterHistoryRefresh"
}

Init ==
  /\ activeSession = SessionA
  /\ activeGroup = "CURRENT_LOANS"
  /\ sessionGeneration = 0
  /\ listRows = [group \in GroupsExpected |-> {}]
  /\ listNextCursor = [group \in GroupsExpected |-> NoCursor]
  /\ listHasLoaded = [group \in GroupsExpected |-> FALSE]
  /\ pageRequestGeneration = [group \in GroupsExpected |-> 0]
  /\ oldPageRequest = NoPageRequest
  /\ currentPageRequest = NoPageRequest
  /\ reservationCanceled = FALSE
  /\ historyInvalidated = FALSE
  /\ tags = {}

CheckFixtureMapping ==
  /\ GroupPartitionContract
  /\ PageContract
  /\ tags' = tags \cup {"GroupPartitionChecked", "OtherKindsExcluded"}
  /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration,
                 listRows, listNextCursor, listHasLoaded, pageRequestGeneration,
                 oldPageRequest, currentPageRequest, reservationCanceled, historyInvalidated>>

SelectGroup(group) ==
  /\ group \in GroupsExpected
  /\ group # activeGroup
  /\ (currentPageRequest = NoPageRequest \/ oldPageRequest = NoPageRequest)
  /\ activeGroup' = group
  /\ IF currentPageRequest = NoPageRequest
       THEN /\ oldPageRequest' = oldPageRequest
            /\ currentPageRequest' = NoPageRequest
       ELSE /\ oldPageRequest' = currentPageRequest
            /\ currentPageRequest' = NoPageRequest
  /\ UNCHANGED <<activeSession, sessionGeneration,
                 listRows, listNextCursor, listHasLoaded, pageRequestGeneration,
                 reservationCanceled, historyInvalidated, tags>>

StartFirstPage(group) ==
  /\ group = activeGroup
  /\ currentPageRequest = NoPageRequest
  /\ listHasLoaded[group] = FALSE
  /\ pageRequestGeneration[group] < MaxRequestGeneration
  /\ LET nextGeneration == pageRequestGeneration[group] + 1
     IN
       /\ currentPageRequest' =
            [group |-> group, session |-> activeSession,
             sessionGeneration |-> sessionGeneration,
             requestGeneration |-> nextGeneration, cursor |-> StartCursor,
             reservationCanceledAtStart |-> reservationCanceled]
       /\ pageRequestGeneration' = [pageRequestGeneration EXCEPT ![group] = nextGeneration]
  /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration,
                 listRows, listNextCursor, listHasLoaded, oldPageRequest,
                 reservationCanceled, historyInvalidated, tags>>

StartMorePage(group) ==
  /\ group = activeGroup
  /\ currentPageRequest = NoPageRequest
  /\ listHasLoaded[group]
  /\ listNextCursor[group] # NoCursor
  /\ pageRequestGeneration[group] < MaxRequestGeneration
  /\ LET nextGeneration == pageRequestGeneration[group] + 1
     IN
       /\ currentPageRequest' =
            [group |-> group, session |-> activeSession,
             sessionGeneration |-> sessionGeneration,
             requestGeneration |-> nextGeneration, cursor |-> listNextCursor[group],
             reservationCanceledAtStart |-> reservationCanceled]
       /\ pageRequestGeneration' = [pageRequestGeneration EXCEPT ![group] = nextGeneration]
  /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration,
                 listRows, listNextCursor, listHasLoaded, oldPageRequest,
                 reservationCanceled, historyInvalidated, tags>>

SupersedeCurrentPage ==
  /\ currentPageRequest # NoPageRequest
  /\ oldPageRequest = NoPageRequest
  /\ oldPageRequest' = currentPageRequest
  /\ currentPageRequest' = NoPageRequest
  /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration,
                 listRows, listNextCursor, listHasLoaded, pageRequestGeneration,
                 reservationCanceled, historyInvalidated, tags>>

SwitchSession(session) ==
  /\ session \in SessionIds
  /\ session # activeSession
  /\ sessionGeneration < MaxSessionGeneration
  /\ (currentPageRequest = NoPageRequest \/ oldPageRequest = NoPageRequest)
  /\ activeSession' = session
  /\ sessionGeneration' = sessionGeneration + 1
  /\ listRows' = [group \in GroupsExpected |-> {}]
  /\ listNextCursor' = [group \in GroupsExpected |-> NoCursor]
  /\ listHasLoaded' = [group \in GroupsExpected |-> FALSE]
  /\ pageRequestGeneration' = [group \in GroupsExpected |-> 0]
  /\ IF currentPageRequest = NoPageRequest
       THEN /\ oldPageRequest' = oldPageRequest
            /\ currentPageRequest' = NoPageRequest
       ELSE /\ oldPageRequest' = currentPageRequest
            /\ currentPageRequest' = NoPageRequest
  /\ UNCHANGED <<activeGroup, reservationCanceled, historyInvalidated, tags>>

CompletePage(request, slot) ==
  LET group == request.group
      reader == SessionReader(request.session)
      current == request.session = activeSession
                  /\ request.sessionGeneration = sessionGeneration
                  /\ request.requestGeneration = pageRequestGeneration[group]
                  /\ request.reservationCanceledAtStart = reservationCanceled
      responseRows == PageRows(reader, group, request.cursor,
                                request.reservationCanceledAtStart)
      updatedRows == IF request.cursor = StartCursor
                       THEN responseRows
                       ELSE listRows[group] \cup responseRows
      updatedNext == PageNextCursor(reader, group, request.cursor)
      updatedMap == [listRows EXCEPT ![group] = updatedRows]
      newRenderTag ==
        IF slot = "CURRENT" /\ current /\ group = activeGroup
          THEN {"NewActivePageRendered"} ELSE {}
      eventTags ==
        IF ~current
          THEN IF request.session # activeSession
                    \/ request.sessionGeneration # sessionGeneration
                 THEN {"StaleAccountPageDiscarded"}
                      \cup (IF slot = "OLD" /\ "NewActivePageRendered" \in tags
                              THEN {"OldAccountResponseAfterNewRendered"} ELSE {})
                 ELSE {"StalePageGenerationDiscarded"}
                      \cup (IF slot = "OLD" /\ "NewActivePageRendered" \in tags
                              THEN {"OldGenerationResponseAfterNewRendered"} ELSE {})
                      \cup (IF slot = "OLD" /\ group = "HISTORY"
                                   /\ reservationCanceled
                                   /\ ~request.reservationCanceledAtStart
                                   /\ "ActiveHistoryRefreshedAfterCancel" \in tags
                              THEN {"OldHistoryResponseAfterHistoryRefresh"} ELSE {})
          ELSE (IF group # activeGroup THEN {"InactiveTabResponseIsolated"} ELSE {})
               \cup (IF slot = "OLD" /\ group # activeGroup
                            /\ "NewActivePageRendered" \in tags
                       THEN {"OldTabResponseAfterNewRendered"} ELSE {})
               \cup (IF request.cursor # StartCursor
                         /\ AllGroupPagesApplied(updatedMap, request.reservationCanceledAtStart)
                       THEN {"IndependentPaginationCompleted"} ELSE {})
               \cup (IF group = "HISTORY" /\ B7Canceled \in updatedRows
                       THEN {"CanceledReservationRefetchedInHistory"} ELSE {})
               \cup (IF reservationCanceled /\ group = "HISTORY"
                         /\ group = activeGroup /\ request.cursor = StartCursor
                       THEN {"ActiveHistoryRefreshedAfterCancel"} ELSE {})
               \cup (IF reservationCanceled /\ group = "RESERVATIONS"
                         /\ request.cursor = StartCursor
                       THEN {"ReservationCacheRefreshedAfterCancel"} ELSE {})
               \cup (IF reservationCanceled /\ group = "RESERVATIONS"
                         /\ group = activeGroup /\ request.cursor = StartCursor
                       THEN {"ActiveReservationRefreshedAfterCancel"} ELSE {})
  IN
    /\ request # NoPageRequest
    /\ IF slot = "OLD"
         THEN /\ oldPageRequest = request
              /\ oldPageRequest' = NoPageRequest
              /\ currentPageRequest' = currentPageRequest
         ELSE /\ currentPageRequest = request
              /\ currentPageRequest' = NoPageRequest
              /\ oldPageRequest' = oldPageRequest
    /\ IF current
         THEN
           /\ listRows' = updatedMap
           /\ listNextCursor' = [listNextCursor EXCEPT ![group] = updatedNext]
           /\ listHasLoaded' = [listHasLoaded EXCEPT ![group] = TRUE]
           /\ historyInvalidated' =
                IF group = "HISTORY" /\ request.cursor = StartCursor
                  THEN FALSE ELSE historyInvalidated
           /\ tags' = tags \cup eventTags \cup newRenderTag
         ELSE
           /\ UNCHANGED <<listRows, listNextCursor, listHasLoaded, historyInvalidated>>
           /\ tags' = tags \cup eventTags
    /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration, pageRequestGeneration,
                   reservationCanceled>>

CompleteCurrentPage == CompletePage(currentPageRequest, "CURRENT")
CompleteOldPage == CompletePage(oldPageRequest, "OLD")

CancelReservationToHistory ==
  /\ activeGroup \in {"RESERVATIONS", "HISTORY"}
  /\ reservationCanceled = FALSE
  /\ listHasLoaded["RESERVATIONS"]
  /\ B7 \in listRows["RESERVATIONS"]
  /\ ((currentPageRequest = NoPageRequest /\ oldPageRequest = NoPageRequest)
       \/ (currentPageRequest # NoPageRequest /\ oldPageRequest = NoPageRequest
             /\ currentPageRequest.group \in {"RESERVATIONS", "HISTORY"})
       \/ (currentPageRequest = NoPageRequest /\ oldPageRequest # NoPageRequest
             /\ oldPageRequest.group \in {"RESERVATIONS", "HISTORY"}))
  /\ reservationCanceled' = TRUE
  /\ historyInvalidated' = TRUE
  /\ listRows' = [listRows EXCEPT !["RESERVATIONS"] = {}, !["HISTORY"] = {}]
  /\ listNextCursor' = [listNextCursor EXCEPT !["RESERVATIONS"] = NoCursor,
                                               !["HISTORY"] = NoCursor]
  /\ listHasLoaded' = [listHasLoaded EXCEPT !["RESERVATIONS"] = FALSE,
                                              !["HISTORY"] = FALSE]
  /\ pageRequestGeneration' =
       [pageRequestGeneration EXCEPT
          !["RESERVATIONS"] = 0,
          !["HISTORY"] = 0]
  /\ IF currentPageRequest = NoPageRequest
       THEN oldPageRequest' = oldPageRequest
       ELSE oldPageRequest' = currentPageRequest
  /\ currentPageRequest' =
       [group |-> activeGroup, session |-> activeSession,
        sessionGeneration |-> sessionGeneration,
        requestGeneration |-> 1,
        cursor |-> StartCursor, reservationCanceledAtStart |-> TRUE]
  /\ tags' = tags \cup {"ReservationCacheInvalidatedOnCancel",
                           "HistoryCacheInvalidatedOnCancel"}
  /\ UNCHANGED <<activeSession, activeGroup, sessionGeneration>>

Next ==
  \/ CheckFixtureMapping
  \/ \E group \in GroupsExpected : SelectGroup(group)
  \/ \E group \in GroupsExpected : StartFirstPage(group)
  \/ \E group \in GroupsExpected : StartMorePage(group)
  \/ SupersedeCurrentPage
  \/ \E session \in SessionIds : SwitchSession(session)
  \/ CompleteCurrentPage
  \/ CompleteOldPage
  \/ CancelReservationToHistory

TypeOK ==
  /\ activeSession \in SessionIds
  /\ activeGroup \in GroupsExpected
  /\ sessionGeneration \in SessionGenerations
  /\ listRows \in [GroupsExpected -> SUBSET BusinessFactSpace]
  /\ listNextCursor \in [GroupsExpected -> CursorValues \cup {NoCursor}]
  /\ listHasLoaded \in [GroupsExpected -> BOOLEAN]
  /\ pageRequestGeneration \in [GroupsExpected -> RequestGenerations]
  /\ (currentPageRequest = NoPageRequest \/ currentPageRequest \in PageRequestSpace)
  /\ (oldPageRequest = NoPageRequest \/ oldPageRequest \in PageRequestSpace)
  /\ reservationCanceled \in BOOLEAN
  /\ historyInvalidated \in BOOLEAN
  /\ tags \subseteq EventTags

InvGroupPartition == GroupPartitionContract

InvPageProjectionInOwnGroup ==
  /\ \A group \in GroupsExpected :
       listRows[group] \subseteq ExpectedRows(SessionReader(activeSession), group,
                                                reservationCanceled)
  /\ (currentPageRequest = NoPageRequest \/
       CursorMatchesGroup(currentPageRequest.group, currentPageRequest.cursor))
  /\ (oldPageRequest = NoPageRequest \/
       CursorMatchesGroup(oldPageRequest.group, oldPageRequest.cursor))
  /\ \A group \in GroupsExpected :
       listNextCursor[group] = NoCursor \/ CursorMatchesGroup(group, listNextCursor[group])

InvPageCursorStateCoherent ==
  \A group \in GroupsExpected :
    (listHasLoaded[group] = FALSE => listRows[group] = {} /\ listNextCursor[group] = NoCursor)

InvVisibleRowsBelongToActiveGroup ==
  listRows[activeGroup] \subseteq ExpectedRows(SessionReader(activeSession), activeGroup,
                                                 reservationCanceled)

InvHistoryCacheEmptyWhileInvalidated ==
  historyInvalidated
    => listRows["HISTORY"] = {} /\ listNextCursor["HISTORY"] = NoCursor
       /\ listHasLoaded["HISTORY"] = FALSE

InvReservationCacheEmptyWhileInvalidated ==
  ("ReservationCacheInvalidatedOnCancel" \in tags
     /\ "ReservationCacheRefreshedAfterCancel" \notin tags)
    => listRows["RESERVATIONS"] = {} /\ listNextCursor["RESERVATIONS"] = NoCursor
       /\ listHasLoaded["RESERVATIONS"] = FALSE

InvCanceledReservationInHistory ==
  /\ (reservationCanceled => B7Canceled \in ExpectedRows(ReaderA, "HISTORY", TRUE))
  /\ (reservationCanceled => B7 \notin ExpectedRows(ReaderA, "RESERVATIONS", TRUE))

ProbeNotGroupPartitionChecked == "GroupPartitionChecked" \notin tags
ProbeNotOtherKindsExcluded == "OtherKindsExcluded" \notin tags
ProbeNotIndependentPagination == "IndependentPaginationCompleted" \notin tags
ProbeNotInactiveTabResponseIsolated == "InactiveTabResponseIsolated" \notin tags
ProbeNotStaleAccountPageDiscarded == "StaleAccountPageDiscarded" \notin tags
ProbeNotStalePageGenerationDiscarded == "StalePageGenerationDiscarded" \notin tags
ProbeNotOldTabResponseAfterNewRendered == "OldTabResponseAfterNewRendered" \notin tags
ProbeNotOldAccountResponseAfterNewRendered == "OldAccountResponseAfterNewRendered" \notin tags
ProbeNotOldGenerationResponseAfterNewRendered == "OldGenerationResponseAfterNewRendered" \notin tags
ProbeNotHistoryRefreshAfterCancel ==
  ~({"HistoryCacheInvalidatedOnCancel", "CanceledReservationRefetchedInHistory"} \subseteq tags)
ProbeNotCurrentHistoryRefreshAfterCancel ==
  "ActiveHistoryRefreshedAfterCancel" \notin tags
ProbeNotOldHistoryResponseAfterHistoryRefresh ==
  "OldHistoryResponseAfterHistoryRefresh" \notin tags
ProbeNotCurrentReservationRefreshAfterCancel ==
  "ActiveReservationRefreshedAfterCancel" \notin tags

=============================================================================
