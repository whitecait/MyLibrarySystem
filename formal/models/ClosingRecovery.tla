------------------------------ MODULE ClosingRecovery ------------------------------
EXTENDS Naturals, FiniteSets
CONSTANTS Dates, D1, D2, WriteBound
ASSUME Dates = {D1, D2} /\ D1 # D2
VARIABLES running, starts, checked, isolated, done, initialDone, processed, failed, writes, horizon, initialHorizon
vars == <<running, starts, checked, isolated, done, initialDone, processed, failed, writes, horizon, initialHorizon>>
AllDone == done[D1] /\ (horizon = 2 => done[D2])

Init ==
  /\ running = FALSE /\ starts = 0 /\ checked = FALSE /\ isolated = TRUE
  /\ horizon \in 1..2 /\ initialHorizon = horizon
  /\ initialDone \in [Dates -> BOOLEAN]
  /\ initialDone[D2] => (initialDone[D1] /\ horizon = 2)
  /\ done = initialDone
  /\ processed = [d \in Dates |-> 0]
  /\ failed = FALSE /\ writes = 0

\* Startup gates ordinary writes before checking persisted backlog. Two starts model one restart.
Start ==
  /\ ~running /\ starts < 2
  /\ running' = TRUE /\ starts' = starts + 1 /\ checked' = TRUE /\ isolated' = TRUE
  /\ writes' = 0
  /\ UNCHANGED <<done, initialDone, processed, failed, horizon, initialHorizon>>
Stop ==
  /\ running /\ starts < 2
  /\ running' = FALSE /\ checked' = FALSE
  /\ UNCHANGED <<starts, isolated, done, initialDone, processed, failed, writes, horizon, initialHorizon>>
CloseSuccess(d) ==
  /\ running /\ checked /\ isolated /\ ~failed /\ ~done[d]
  /\ (d = D2 => (done[D1] /\ horizon = 2))
  /\ done' = [done EXCEPT ![d] = TRUE]
  /\ processed' = [processed EXCEPT ![d] = @ + 1]
  /\ UNCHANGED <<running, starts, checked, isolated, initialDone, failed, writes, horizon, initialHorizon>>
CloseFailure ==
  /\ running /\ checked /\ isolated /\ ~failed /\ ~AllDone
  /\ failed' = TRUE
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, writes, horizon, initialHorizon>>
Repair ==
  /\ failed /\ isolated
  /\ failed' = FALSE
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, writes, horizon, initialHorizon>>
AutomaticOpen ==
  /\ running /\ checked /\ isolated /\ ~failed /\ AllDone
  /\ isolated' = FALSE
  /\ UNCHANGED <<running, starts, checked, done, initialDone, processed, failed, writes, horizon, initialHorizon>>
Write ==
  /\ running /\ checked /\ ~isolated /\ writes < WriteBound
  /\ writes' = writes + 1
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, failed, horizon, initialHorizon>>
\* A new business day can become due while startup is still isolated.
MidnightDuringRecovery ==
  /\ running /\ checked /\ isolated /\ writes = 0 /\ horizon = 1
  /\ horizon' = 2
  /\ UNCHANGED <<running, starts, checked, isolated, done, initialDone, processed, failed, writes, initialHorizon>>
Next == Start \/ Stop \/ (\E d \in Dates : CloseSuccess(d)) \/ CloseFailure \/ Repair \/ AutomaticOpen \/ Write \/ MidnightDuringRecovery

TypeOK ==
  /\ running \in BOOLEAN /\ checked \in BOOLEAN /\ isolated \in BOOLEAN /\ failed \in BOOLEAN
  /\ starts \in 0..2 /\ writes \in 0..WriteBound
  /\ horizon \in 1..2 /\ initialHorizon \in 1..2
  /\ done \in [Dates -> BOOLEAN] /\ initialDone \in [Dates -> BOOLEAN]
  /\ processed \in [Dates -> 0..1]
InvOrdered == done[D2] => done[D1]
InvSuccessfulDatesNotRepeated == \A d \in Dates : initialDone[d] => processed[d] = 0
InvOpenOnlyWhenCaughtUp == ~isolated => AllDone
InvFailureKeepsIsolation == failed => isolated
InvWritesAfterRecovery == writes > 0 => AllDone
InvNoFutureDateClosed == horizon = 1 => processed[D2] = 0

ProbeNotStartupCatchUp == ~(starts = 1 /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotTwoMissingDatesRecovered == ~(~initialDone[D1] /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotRestartRecovery == ~(starts = 2 /\ ~initialDone[D2] /\ AllDone /\ ~isolated)
ProbeNotPreservedSuccessfulDate == ~(initialDone[D1] /\ ~initialDone[D2] /\ done[D2] /\ processed[D1] = 0)
ProbeNotFailureIsolation == ~(failed /\ isolated /\ ~AllDone)
ProbeNotStartupCrossedMidnight == ~(starts = 1 /\ initialHorizon = 1 /\ horizon = 2 /\ processed[D2] = 1 /\ ~isolated)
=============================================================================
