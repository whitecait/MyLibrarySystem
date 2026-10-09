------------------------------ MODULE BorrowingLogin ------------------------------
EXTENDS Naturals
VARIABLES form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, event, route, loginCompleted
vars == <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, event, route, loginCompleted>>
Forms == [date : {"D1", "D2"}, slot : {"S1", "S2"}]
Init ==
  /\ form \in Forms /\ savedForm = form /\ prompt = FALSE
  /\ authenticated \in BOOLEAN /\ expired \in BOOLEAN /\ loginCompleted \in BOOLEAN
  /\ writes = 0 /\ writesAtPrompt = 0 /\ event = "INIT" /\ route = "BORROW"
  /\ loginCompleted = FALSE
Edit(nextForm) ==
  /\ ~prompt /\ form' = nextForm /\ event' = "EDIT"
  /\ UNCHANGED <<savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted>>
Submit ==
  /\ ~prompt /\ writes < 2
  /\ IF authenticated /\ ~expired
      THEN /\ writes' = writes + 1 /\ event' = "SUBMITTED" /\ prompt' = FALSE
           /\ UNCHANGED <<authenticated, expired, savedForm, writesAtPrompt>>
      ELSE /\ prompt' = TRUE /\ savedForm' = form /\ writesAtPrompt' = writes
           /\ event' = IF authenticated THEN "EXPIRED" ELSE "ANONYMOUS"
           /\ authenticated' = FALSE /\ expired' = FALSE /\ UNCHANGED writes
  /\ UNCHANGED <<form, route, loginCompleted>>
LoginFailed ==
  /\ prompt /\ event' = "LOGIN_FAILED"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted>>
LoginSuccess ==
  /\ prompt /\ prompt' = FALSE /\ authenticated' = TRUE /\ expired' = FALSE
  /\ event' = "LOGIN_SUCCESS" /\ loginCompleted' = TRUE
  /\ UNCHANGED <<form, savedForm, writes, writesAtPrompt, route>>
Cancel ==
  /\ prompt /\ prompt' = FALSE /\ event' = "CANCELED"
  /\ UNCHANGED <<form, savedForm, authenticated, expired, writes, writesAtPrompt, route, loginCompleted>>
Next == (\E f \in Forms : Edit(f)) \/ Submit \/ LoginFailed \/ LoginSuccess \/ Cancel
TypeOK ==
  /\ form \in Forms /\ savedForm \in Forms /\ prompt \in BOOLEAN
  /\ authenticated \in BOOLEAN /\ expired \in BOOLEAN /\ loginCompleted \in BOOLEAN
  /\ writes \in 0..2 /\ writesAtPrompt \in 0..2
InvPromptFrozen == prompt => form = savedForm /\ writes = writesAtPrompt
InvAuthenticationPreservesDraft == event \in {"LOGIN_FAILED", "LOGIN_SUCCESS", "CANCELED"} => form = savedForm
InvNoAutomaticWrite == event \in {"LOGIN_FAILED", "LOGIN_SUCCESS", "CANCELED"} => writes = writesAtPrompt
InvRoutePreserved == route = "BORROW"
InvSubmissionAuthenticated == event = "SUBMITTED" => authenticated /\ ~expired /\ ~prompt
ProbeNotAnonymousPrompt == ~(event = "ANONYMOUS" /\ prompt /\ writes = 0)
ProbeNotExpiredPrompt == ~(event = "EXPIRED" /\ prompt /\ writes = 0)
ProbeNotLoginFailureKeepsDraft == ~(event = "LOGIN_FAILED" /\ prompt /\ form = savedForm)
ProbeNotCancelKeepsDraft == ~(event = "CANCELED" /\ ~prompt /\ form = savedForm)
ProbeNotSuccessWithoutWrite == ~(event = "LOGIN_SUCCESS" /\ ~prompt /\ writes = 0 /\ form = savedForm)
ProbeNotExplicitResubmit == ~(event = "SUBMITTED" /\ writes = 1 /\ authenticated /\ loginCompleted /\ form = savedForm)
=============================================================================
