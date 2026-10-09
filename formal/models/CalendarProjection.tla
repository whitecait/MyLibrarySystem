------------------------------ MODULE CalendarProjection ------------------------------
EXTENDS Naturals, FiniteSets
VARIABLES copies, booked, past, duePassed, initialLent, returned, snapshot, display, queried
vars == <<copies, booked, past, duePassed, initialLent, returned, snapshot, display, queried>>
Copies == 1..3
Days == 1..2
CopyStates == {"READY", "RESERVED", "LENT", "PENDING"}
Priority(cs, rs, isPast, day) ==
  IF isPast THEN "NONE"
  ELSE IF \E c \in Copies : cs[c] = "READY" THEN "AVAILABLE"
  ELSE IF rs[day] THEN "RESERVED"
  ELSE IF \E c \in Copies : cs[c] = "LENT" THEN "LENT"
  ELSE "NONE"
Init ==
  /\ copies \in [Copies -> CopyStates]
  /\ booked \in [Days -> BOOLEAN]
  /\ past \in BOOLEAN /\ duePassed = FALSE
  /\ initialLent = {c \in Copies : copies[c] = "LENT"}
  /\ returned = {} /\ queried = FALSE
  /\ snapshot = [copies |-> copies, booked |-> booked, past |-> past]
  /\ display = [d \in Days |-> "NONE"]
PassDueDate ==
  /\ ~duePassed /\ duePassed' = TRUE
  /\ UNCHANGED <<copies, booked, past, initialLent, returned, snapshot, display, queried>>
ActualReturn(c) ==
  /\ copies[c] = "LENT"
  /\ copies' = [copies EXCEPT ![c] = "PENDING"]
  /\ returned' = returned \cup {c}
  /\ UNCHANGED <<booked, past, duePassed, initialLent, snapshot, display, queried>>
Query ==
  /\ snapshot' = [copies |-> copies, booked |-> booked, past |-> past]
  /\ display' = [d \in Days |-> Priority(copies, booked, past, d)]
  /\ queried' = TRUE
  /\ UNCHANGED <<copies, booked, past, duePassed, initialLent, returned>>
Next == PassDueDate \/ (\E c \in Copies : ActualReturn(c)) \/ Query
TypeOK ==
  /\ copies \in [Copies -> CopyStates] /\ booked \in [Days -> BOOLEAN]
  /\ past \in BOOLEAN /\ duePassed \in BOOLEAN /\ queried \in BOOLEAN
  /\ initialLent \subseteq Copies /\ returned \subseteq initialLent
  /\ display \in [Days -> {"AVAILABLE", "RESERVED", "LENT", "NONE"}]
InvSnapshotProjection == queried => \A d \in Days :
  display[d] = Priority(snapshot.copies, snapshot.booked, snapshot.past, d)
InvLoanReleasedOnlyByReturn == \A c \in initialLent \ returned : copies[c] = "LENT"
InvReturnedCopyNotImmediatelyAvailable == \A c \in returned : copies[c] = "PENDING"
InvPastNeutral == queried /\ snapshot.past => \A d \in Days : display[d] = "NONE"
InvReservationDateLocal == queried /\ ~snapshot.past /\ ~snapshot.booked[2]
  /\ ~(\E c \in Copies : snapshot.copies[c] = "READY") => display[2] # "RESERVED"
ProbeNotAvailablePriority == ~(queried /\ ~past /\ booked[1] /\ copies[1] = "READY"
  /\ copies[2] = "RESERVED" /\ copies[3] = "LENT" /\ display[1] = "AVAILABLE")
ProbeNotReservedPriority == ~(queried /\ ~past /\ booked[1] /\ display[1] = "RESERVED"
  /\ (\E c \in Copies : snapshot.copies[c] = "LENT"))
ProbeNotDuePassedStillLent == ~(queried /\ ~past /\ duePassed /\ display[2] = "LENT"
  /\ (\E c \in initialLent \ returned : snapshot.copies[c] = "LENT"))
ProbeNotActualReturnClearsLent == ~(queried /\ ~past /\ initialLent # {}
  /\ returned = initialLent /\ snapshot.copies = copies /\ display[2] = "NONE")
ProbeNotPastNeutral == ~(queried /\ past /\ display[1] = "NONE"
  /\ (\E c \in Copies : snapshot.copies[c] = "READY"))
ProbeNotNoFactsNeutral == ~(queried /\ ~past /\ display[1] = "NONE" /\ initialLent = {})
=============================================================================
