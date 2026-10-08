------------------------------ MODULE ClosingRecovery ------------------------------
EXTENDS Naturals, FiniteSets
CONSTANTS Dates, D1, D2, WriteBound
ASSUME Dates = {D1, D2} /\ D1 # D2
VARIABLES running, starts, checked, isolated, done, initialDone, processed, failed, writes
vars == <<running, starts, checked, isolated, done, initialDone, processed, failed, writes>>
AllDone == \A d \in Dates : done[d]

Init ==
  /\ running = FALSE /\ starts = 0 /\ checked = FALSE /\ isolated = TRUE
  /\ initialDone \in [Dates -> BOOLEAN]
  /\ initialDone[D2] => initialDone[D1]
  /\ done = initialDone
  /\ processed = [d \in Dates |-> 0]
  /\ failed = FALSE /\ writes = 0

\* Startup gates ordinary writes before checking persisted backlog. Two starts model one restart.
Start ==
  /\ ~running /\ starts < 2
  /\ running' = TRUE /\ starts' = starts + 1 /\ checked' = TRUE /\ isolated' = TRUE
  /\ UNCHANGED <<done, initialDone, processed, failed, writes>>
Stop ==
  /\ running /\ starts < 2
  /\ running' = FALSE /\ checked' = FALSE
  /\ UNCHANGED <<starts, isolated, done, initialDone, processed, failed, writes>>
CloseSuccess(d) ==
  /\ running /\ checked /\ isolated /\ ~failed /\ ~done[d]
  /\ (d = D2 => done[D1])
  /\ done' = [done EXCEPT ![d] = TRUE]
  /\ processed' = [processed EXCEPT ![d] = @ + 1]
  /\ UNCHANGED <<running, starts, checked, isolated, initialDone, failed, writes>>
CloseFailure ==
  /\ running /\ checked /\ isolated /\ ~failed /\ ~AllDone
  /\ failed' = TRUE
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, writes>>
Repair ==
  /\ failed /\ isolated
  /\ failed' = FALSE
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, writes>>
AutomaticOpen ==
  /\ running /\ checked /\ isolated /\ ~failed /\ AllDone
  /\ isolated' = FALSE
  /\ UNCHANGED <<running, starts, checked, done, initialDone, processed, failed, writes>>
Write ==
  /\ running /\ checked /\ ~isolated /\ writes < WriteBound
  /\ writes' = writes + 1
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, failed>>
Next == Start \/ Stop \/ (\E d \in Dates : CloseSuccess(d)) \/ CloseFailure \/ Repair \/ AutomaticOpen \/ Write

TypeOK ==
  /\ running \in BOOLEAN /\ checked \in BOOLEAN /\ isolated \in BOOLEAN /\ failed \in BOOLEAN
  /\ starts \in 0..2 /\ writes \in 0..WriteBound
  /\ done \in [Dates -> BOOLEAN] /\ initialDone \in [Dates -> BOOLEAN]
  /\ processed \in [Dates -> 0..1]
InvOrdered == done[D2] => done[D1]
InvSuccessfulDatesNotRepeated == \A d \in Dates : initialDone[d] => processed[d] = 0
InvOpenOnlyWhenCaughtUp == ~isolated => AllDone
InvFailureKeepsIsolation == failed => isolated
InvWritesAfterRecovery == writes > 0 => AllDone

ProbeNotStartupCatchUp == ~(starts = 1 /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotTwoMissingDatesRecovered == ~(~initialDone[D1] /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotRestartRecovery == ~(starts = 2 /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotPreservedSuccessfulDate == ~(initialDone[D1] /\ ~initialDone[D2] /\ done[D2] /\ processed[D1] = 0)
ProbeNotFailureIsolation == ~(failed /\ isolated /\ ~AllDone)
=============================================================================
