-------------------------- MODULE NotificationNavigation --------------------------
(***************************************************************************)
(* GOV-0008 / AT-0002 P5 presentation slice: one nearest unread banner,     *)
(* read-success advancement, banner navigation, and reader-owned detail     *)
(* projection. The finite fixture intentionally includes a same-ID FINE and *)
(* ORDER beside a LOAN, plus foreign-only and missing route targets.        *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  Readers, SessionIds,
  Notifications,
  TransactionIds, Dates, D0, D1,
  ReservationKind, LoanKind, FineKind, OrderKind,
  ReservationStatuses, LoanStatuses,
  InitialUnread,
  RequestIds, MaxGeneration, MaxCenterPage,
  NoSession, NoNotification, NoTransaction

VARIABLES
  activeSession, unreadByReader, pendingNotificationSessions,
  sourceSession, loadedUnread, banner, centerPage,
  routeTransaction, routeSession, routeGeneration, routeOrigin, clickedNotification,
  pendingDetailRequests, detailRecord, tags

ASSUME D0 \in Dates /\ D1 \in Dates /\ D0 # D1
ASSUME InitialUnread \subseteq Notifications

SessionReader(session) ==
  IF session = "session-b1" THEN "reader-b" ELSE "reader-a"

NotificationOwner(notification) ==
  IF notification = "n-foreign" THEN "reader-b" ELSE "reader-a"

NotificationTx(notification) ==
  IF notification = "n-soon" THEN "tx-soon"
  ELSE IF notification = "n-same-day" THEN "tx-loan"
  ELSE IF notification = "n-later" THEN "tx-later"
  ELSE "tx-foreign"

NotificationDate(notification) ==
  IF notification = "n-later" THEN D1 ELSE D0

BusinessFacts == {
  <<"reader-a", "RESERVATION", "tx-soon", "CONFIRMED">>,
  <<"reader-a", "LOAN", "tx-loan", "RETURNED">>,
  <<"reader-a", "RESERVATION", "tx-later", "WAITLIST">>,
  <<"reader-a", "FINE", "tx-loan", "UNPAID">>,
  <<"reader-a", "ORDER", "tx-loan", "OPEN">>,
  <<"reader-a", "FINE", "tx-fine-only", "UNPAID">>,
  <<"reader-a", "ORDER", "tx-fine-only", "OPEN">>,
  <<"reader-b", "RESERVATION", "tx-foreign", "CONFIRMED">>
}

NoBusinessRow ==
  <<"NO_READER", "NO_KIND", "NO_TRANSACTION", "NO_STATUS">>

vars == <<activeSession, unreadByReader, pendingNotificationSessions,
          sourceSession, loadedUnread, banner, centerPage,
          routeTransaction, routeSession, routeGeneration,
          pendingDetailRequests, detailRecord, tags>>

EventTags == {
  "NearestSelected", "BannerReadAdvanced", "BannerHiddenWhenEmpty",
  "PageLoadedWithBanner", "ClickKeptUnread", "StaleNotificationDiscarded",
  "RelatedIdPreserved", "StaleDetailDiscarded", "BorrowKindWonCollision",
  "FineOrderRejected", "ForeignRowRejected", "MissingRowRejected"
}

RouteOrigins == {"NONE", "BANNER", "DIRECT"}

Kinds == {ReservationKind, LoanKind, FineKind, OrderKind}
DetailGenerations == 0..MaxGeneration

DetailRequestSpace ==
  [reqId : RequestIds, session : SessionIds,
   tx : TransactionIds, generation : DetailGenerations]

DateBefore(earlier, later) == earlier = D0 /\ later = D1

UnreadBusinessFor(reader, unread) ==
  {n \in Notifications :
      NotificationOwner(n) = reader /\ n \in unread
        /\ NotificationTx(n) \in TransactionIds
        /\ NotificationDate(n) \in Dates}

Nearest(candidates) ==
  {n \in candidates :
      \A other \in candidates :
        ~DateBefore(NotificationDate(other), NotificationDate(n))}

ReaderOf(session) == SessionReader(session)

BorrowRowsFor(reader, tx) ==
  {row \in BusinessFacts :
      row[1] = reader /\ row[3] = tx /\
      ((row[2] = ReservationKind /\ row[4] \in ReservationStatuses) \/
       (row[2] = LoanKind /\ row[4] \in LoanStatuses))}

RowsFor(reader, tx) ==
  {row \in BusinessFacts : row[1] = reader /\ row[3] = tx}

HasFineOrderOnly(reader, tx) ==
  /\ BorrowRowsFor(reader, tx) = {}
  /\ \E row \in BusinessFacts :
       row[1] = reader /\ row[3] = tx /\ row[2] \in {FineKind, OrderKind}

HasForeignBorrowOnly(reader, tx) ==
  /\ BorrowRowsFor(reader, tx) = {}
  /\ \E row \in BusinessFacts :
       row[1] # reader /\ row[3] = tx
         /\ ((row[2] = ReservationKind /\ row[4] \in ReservationStatuses) \/
             (row[2] = LoanKind /\ row[4] \in LoanStatuses))

HasNoRows(tx) == \A row \in BusinessFacts : row[3] # tx

HasBorrowKindCollision(reader, tx) ==
  /\ BorrowRowsFor(reader, tx) # {}
  /\ \E row \in BusinessFacts :
       row[1] = reader /\ row[3] = tx /\ row[2] \in {FineKind, OrderKind}

Init ==
  /\ activeSession \in SessionIds
  /\ unreadByReader =
       [reader \in Readers |->
          {n \in Notifications :
             NotificationOwner(n) = reader /\ n \in InitialUnread}]
  /\ pendingNotificationSessions = {}
  /\ sourceSession = NoSession
  /\ loadedUnread = {}
  /\ banner = NoNotification
  /\ centerPage = 0
  /\ routeTransaction = NoTransaction
  /\ routeSession = NoSession
  /\ routeGeneration = 0
  /\ routeOrigin = "NONE"
  /\ clickedNotification = NoNotification
  /\ pendingDetailRequests = {}
  /\ detailRecord = NoBusinessRow
  /\ tags = {}

StartNotificationFetch ==
  /\ pendingNotificationSessions = {}
  /\ pendingNotificationSessions' = pendingNotificationSessions \cup {activeSession}
  /\ UNCHANGED <<activeSession, unreadByReader, sourceSession, loadedUnread,
                 banner, centerPage, routeTransaction, routeSession,
                 routeGeneration, routeOrigin, clickedNotification,
                 pendingDetailRequests, detailRecord, tags>>

CompleteNotificationFetch(session) ==
  /\ session \in pendingNotificationSessions
  /\ pendingNotificationSessions' = pendingNotificationSessions \ {session}
  /\ IF session = activeSession
       THEN
         LET loaded == unreadByReader[ReaderOf(session)]
             candidates == UnreadBusinessFor(ReaderOf(session), loaded)
         IN
           /\ sourceSession' = session
           /\ loadedUnread' = loaded
           /\ IF candidates = {}
                THEN /\ banner' = NoNotification
                     /\ tags' = tags
                ELSE /\ banner' \in Nearest(candidates)
                     /\ tags' =
                          IF \E n, later \in candidates :
                               DateBefore(NotificationDate(n), NotificationDate(later))
                          THEN tags \cup {"NearestSelected"}
                          ELSE tags
       ELSE
         /\ UNCHANGED <<sourceSession, loadedUnread, banner>>
         /\ tags' = tags \cup {"StaleNotificationDiscarded"}
  /\ UNCHANGED <<activeSession, unreadByReader, centerPage,
                 routeTransaction, routeSession, routeGeneration,
                 routeOrigin, clickedNotification,
                 pendingDetailRequests, detailRecord>>

SwitchSession(session) ==
  /\ session \in SessionIds
  /\ session # activeSession
  /\ routeGeneration < MaxGeneration
  /\ activeSession' = session
  /\ sourceSession' = NoSession
  /\ loadedUnread' = {}
  /\ banner' = NoNotification
  /\ centerPage' = 0
  /\ routeTransaction' = NoTransaction
  /\ routeSession' = NoSession
  /\ routeGeneration' = routeGeneration + 1
  /\ routeOrigin' = "NONE"
  /\ clickedNotification' = NoNotification
  /\ detailRecord' = NoBusinessRow
  /\ UNCHANGED <<unreadByReader, pendingNotificationSessions,
                 pendingDetailRequests, tags>>

LoadCenterPage ==
  /\ sourceSession = activeSession
  /\ centerPage < MaxCenterPage
  /\ centerPage' = centerPage + 1
  /\ tags' = IF banner # NoNotification
               THEN tags \cup {"PageLoadedWithBanner"}
               ELSE tags
  /\ UNCHANGED <<activeSession, unreadByReader, pendingNotificationSessions,
                 sourceSession, loadedUnread, banner, routeTransaction,
                 routeSession, routeGeneration, routeOrigin, clickedNotification,
                 pendingDetailRequests,
                 detailRecord>>

ReadSuccess(notification) ==
  /\ sourceSession = activeSession
  /\ notification \in loadedUnread
  /\ notification \in unreadByReader[ReaderOf(activeSession)]
  /\ LET reader == ReaderOf(activeSession)
         nextUnread == loadedUnread \ {notification}
         candidates == UnreadBusinessFor(reader, nextUnread)
     IN
       /\ unreadByReader' =
            [unreadByReader EXCEPT ![reader] = @ \ {notification}]
       /\ loadedUnread' = nextUnread
       /\ IF banner = notification
            THEN
              IF candidates = {}
                THEN /\ banner' = NoNotification
                     /\ tags' = tags \cup {"BannerHiddenWhenEmpty"}
                ELSE /\ banner' \in Nearest(candidates)
                     /\ tags' = tags \cup {"BannerReadAdvanced"}
            ELSE
              /\ banner' = banner
              /\ tags' = tags
  /\ UNCHANGED <<activeSession, pendingNotificationSessions, sourceSession,
                 centerPage, routeTransaction, routeSession, routeGeneration,
                 routeOrigin, clickedNotification,
                 pendingDetailRequests, detailRecord>>

ClickBanner ==
  /\ sourceSession = activeSession
  /\ banner # NoNotification
  /\ routeGeneration < MaxGeneration
  /\ routeTransaction' = NotificationTx(banner)
  /\ routeSession' = activeSession
  /\ routeGeneration' = routeGeneration + 1
  /\ routeOrigin' = "BANNER"
  /\ clickedNotification' = banner
  /\ detailRecord' = NoBusinessRow
  /\ tags' = tags \cup {"ClickKeptUnread", "RelatedIdPreserved"}
  /\ UNCHANGED <<activeSession, unreadByReader, pendingNotificationSessions,
                 sourceSession, loadedUnread, banner, centerPage,
                 pendingDetailRequests>>

OpenDetail(tx) ==
  /\ tx \in TransactionIds
  /\ routeGeneration < MaxGeneration
  /\ routeTransaction' = tx
  /\ routeSession' = activeSession
  /\ routeGeneration' = routeGeneration + 1
  /\ routeOrigin' = "DIRECT"
  /\ clickedNotification' = NoNotification
  /\ detailRecord' = NoBusinessRow
  /\ UNCHANGED <<activeSession, unreadByReader, pendingNotificationSessions,
                 sourceSession, loadedUnread, banner, centerPage,
                 pendingDetailRequests, tags>>

StartDetailRequest(requestId) ==
  /\ requestId \in RequestIds
  /\ routeTransaction \in TransactionIds
  /\ routeSession = activeSession
  /\ pendingDetailRequests = {}
  /\ pendingDetailRequests' = pendingDetailRequests \cup
       {[reqId |-> requestId, session |-> activeSession,
         tx |-> routeTransaction, generation |-> routeGeneration]}
  /\ UNCHANGED <<activeSession, unreadByReader, pendingNotificationSessions,
                 sourceSession, loadedUnread, banner, centerPage,
                 routeTransaction, routeSession, routeGeneration,
                 routeOrigin, clickedNotification, detailRecord, tags>>

CompleteDetailRequest(requestId) ==
  LET matchingRequests ==
        {request \in pendingDetailRequests : request.reqId = requestId}
      request == CHOOSE request \in matchingRequests : TRUE
      reader == ReaderOf(request.session)
      matches == BorrowRowsFor(reader, request.tx)
      current ==
        /\ request.session = activeSession
        /\ request.session = routeSession
        /\ request.tx = routeTransaction
        /\ request.generation = routeGeneration
  IN
    /\ matchingRequests # {}
    /\ pendingDetailRequests' = pendingDetailRequests \ {request}
    /\ IF current
         THEN
           /\ IF matches = {}
                THEN /\ detailRecord' = NoBusinessRow
                     /\ tags' = tags \cup
                          (IF HasFineOrderOnly(reader, request.tx)
                             THEN {"FineOrderRejected"}
                             ELSE IF HasForeignBorrowOnly(reader, request.tx)
                                    THEN {"ForeignRowRejected"}
                                    ELSE IF HasNoRows(request.tx)
                                           THEN {"MissingRowRejected"}
                                           ELSE {})
                ELSE /\ detailRecord' \in matches
                     /\ tags' = tags \cup
                          (IF HasBorrowKindCollision(reader, request.tx)
                             THEN {"BorrowKindWonCollision"}
                             ELSE {})
         ELSE
           /\ UNCHANGED detailRecord
           /\ tags' = tags \cup {"StaleDetailDiscarded"}
    /\ UNCHANGED <<activeSession, unreadByReader, pendingNotificationSessions,
                   sourceSession, loadedUnread, banner, centerPage,
                   routeTransaction, routeSession, routeGeneration,
                   routeOrigin, clickedNotification>>

Next ==
  \/ StartNotificationFetch
  \/ \E session \in SessionIds : CompleteNotificationFetch(session)
  \/ \E session \in SessionIds : SwitchSession(session)
  \/ LoadCenterPage
  \/ \E notification \in Notifications : ReadSuccess(notification)
  \/ ClickBanner
  \/ \E tx \in TransactionIds : OpenDetail(tx)
  \/ \E requestId \in RequestIds : StartDetailRequest(requestId)
  \/ \E requestId \in RequestIds : CompleteDetailRequest(requestId)

TypeOK ==
  /\ activeSession \in SessionIds
  /\ unreadByReader \in [Readers -> SUBSET Notifications]
  /\ pendingNotificationSessions \subseteq SessionIds
  /\ sourceSession \in SessionIds \cup {NoSession}
  /\ loadedUnread \subseteq Notifications
  /\ banner \in Notifications \cup {NoNotification}
  /\ centerPage \in 0..MaxCenterPage
  /\ routeTransaction \in TransactionIds \cup {NoTransaction}
  /\ routeSession \in SessionIds \cup {NoSession}
  /\ routeGeneration \in DetailGenerations
  /\ routeOrigin \in RouteOrigins
  /\ clickedNotification \in Notifications \cup {NoNotification}
  /\ pendingDetailRequests \subseteq DetailRequestSpace
  /\ Cardinality(pendingDetailRequests) <= 1
  /\ detailRecord \in BusinessFacts \cup {NoBusinessRow}
  /\ tags \subseteq EventTags

InvOneBanner == banner \in Notifications \cup {NoNotification}

InvBannerNearest ==
  LET candidates ==
        IF sourceSession = NoSession
          THEN {}
          ELSE UnreadBusinessFor(ReaderOf(sourceSession), loadedUnread)
  IN
    /\ (sourceSession # activeSession =>
          banner = NoNotification /\ loadedUnread = {})
    /\ (sourceSession = activeSession =>
          /\ ((banner = NoNotification) <=> (candidates = {}))
          /\ (candidates # {} => banner \in Nearest(candidates)))

InvSourceReaderOwnsUnread ==
  /\ (sourceSession = NoSession => loadedUnread = {})
  /\ (sourceSession \in SessionIds =>
        \A n \in loadedUnread :
          NotificationOwner(n) = ReaderOf(sourceSession)
            /\ n \in unreadByReader[ReaderOf(sourceSession)])

InvDetailIsCurrentBorrowRow ==
  detailRecord = NoBusinessRow \/
    /\ detailRecord \in BusinessFacts
    /\ routeSession = activeSession
    /\ routeTransaction = detailRecord[3]
    /\ detailRecord[1] = ReaderOf(activeSession)
    /\ ((detailRecord[2] = ReservationKind
           /\ detailRecord[4] \in ReservationStatuses) \/
        (detailRecord[2] = LoanKind
           /\ detailRecord[4] \in LoanStatuses))

InvBannerRouteMatchesRelatedId ==
  (routeOrigin = "BANNER") =>
    (clickedNotification \in Notifications
       /\ routeTransaction = NotificationTx(clickedNotification))

ProbeNotNearestSelection == "NearestSelected" \notin tags
ProbeNotReadAdvances == "BannerReadAdvanced" \notin tags
ProbeNotReadHides == "BannerHiddenWhenEmpty" \notin tags
ProbeNotPageKeepsBanner == "PageLoadedWithBanner" \notin tags
ProbeNotBannerClickKeepsUnread == "ClickKeptUnread" \notin tags
ProbeNotRelatedIdPreserved == "RelatedIdPreserved" \notin tags
ProbeNotStaleNotificationDiscarded == "StaleNotificationDiscarded" \notin tags
ProbeNotStaleDetailDiscarded == "StaleDetailDiscarded" \notin tags
ProbeNotBorrowKindWinsCollision == "BorrowKindWonCollision" \notin tags
ProbeNotFineOrderRejected == "FineOrderRejected" \notin tags
ProbeNotForeignRowRejected == "ForeignRowRejected" \notin tags
ProbeNotMissingRowRejected == "MissingRowRejected" \notin tags

=============================================================================
