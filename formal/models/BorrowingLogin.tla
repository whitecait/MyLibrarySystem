------------------------------ MODULE BorrowingLogin ------------------------------
EXTENDS Naturals
VARIABLES form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, event, route, loginCompleted, mode, registrationCompleted
vars == <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, event, route, loginCompleted, mode, registrationCompleted>>

Forms == [date : {"D1", "D2"}, slot : {"S1", "S2"}]
AuthenticationEvents == {"LOGIN_FAILED", "LOGIN_SUCCESS", "CANCELED", "REGISTER_OPEN", "REGISTER_FAILED", "REGISTER_SUCCESS", "RETURN_LOGIN"}
Init ==
  /\ form \in Forms /\ savedForm = form /\ prompt = FALSE
  /\ authenticated \in BOOLEAN /\ expired \in BOOLEAN
  /\ writes = 0 /\ writesAtPrompt = 0 /\ event = "INIT" /\ route = "BORROW"
  /\ loginCompleted = FALSE /\ mode = "LOGIN" /\ registrationCompleted = FALSE
Edit(nextForm) ==
  /\ ~prompt /\ form' = nextForm /\ event' = "EDIT"
  /\ UNCHANGED <<savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, mode, registrationCompleted>>

PromptForAuthentication ==
  /\ ~prompt /\ writes < 2 /\ (~authenticated \/ expired)
  /\ prompt' = TRUE /\ savedForm' = form /\ writesAtPrompt' = writes
  /\ event' = IF authenticated THEN "EXPIRED" ELSE "ANONYMOUS"
  /\ authenticated' = FALSE /\ expired' = FALSE
  /\ mode' = "LOGIN" /\ registrationCompleted' = FALSE
  /\ UNCHANGED <<form, writes, route, loginCompleted>>

Submit ==
  /\ ~prompt /\ authenticated /\ ~expired /\ writes < 2
  /\ writes' = writes + 1 /\ event' = "SUBMITTED"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writesAtPrompt, route, loginCompleted, mode, registrationCompleted>>

LoginFailed ==
  /\ prompt /\ mode = "LOGIN" /\ event' = "LOGIN_FAILED"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, mode, registrationCompleted>>

LoginSuccess ==
  /\ prompt /\ mode = "LOGIN"
  /\ prompt' = FALSE /\ authenticated' = TRUE /\ expired' = FALSE
  /\ event' = "LOGIN_SUCCESS" /\ loginCompleted' = TRUE
  /\ UNCHANGED <<form, savedForm, writes, writesAtPrompt, route, mode, registrationCompleted>>

Cancel ==
  /\ prompt /\ prompt' = FALSE /\ event' = "CANCELED"
  /\ UNCHANGED <<form, savedForm, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, mode, registrationCompleted>>

OpenRegistration ==
  /\ prompt /\ mode = "LOGIN"
  /\ mode' = "REGISTER" /\ event' = "REGISTER_OPEN"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, registrationCompleted>>

RegistrationFailed ==
  /\ prompt /\ mode = "REGISTER" /\ ~registrationCompleted
  /\ event' = "REGISTER_FAILED"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, mode, registrationCompleted>>

RegistrationSuccess ==
  /\ prompt /\ mode = "REGISTER" /\ ~registrationCompleted
  /\ registrationCompleted' = TRUE /\ event' = "REGISTER_SUCCESS"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, mode>>

ReturnToLogin ==
  /\ prompt /\ mode = "REGISTER"
  /\ mode' = "LOGIN" /\ event' = "RETURN_LOGIN"
  /\ UNCHANGED <<form, savedForm, prompt, authenticated, expired, writes, writesAtPrompt, route, loginCompleted, registrationCompleted>>

Next == (\E f \in Forms : Edit(f)) \/ PromptForAuthentication \/ Submit \/ LoginFailed \/ LoginSuccess \/ Cancel
  \/ OpenRegistration \/ RegistrationFailed \/ RegistrationSuccess \/ ReturnToLogin
TypeOK ==
  /\ form \in Forms /\ savedForm \in Forms /\ prompt \in BOOLEAN
  /\ authenticated \in BOOLEAN /\ expired \in BOOLEAN /\ loginCompleted \in BOOLEAN
  /\ writes \in 0..2 /\ writesAtPrompt \in 0..2
  /\ mode \in {"LOGIN", "REGISTER"} /\ registrationCompleted \in BOOLEAN
InvPromptFrozen == prompt => form = savedForm /\ writes = writesAtPrompt
InvAuthenticationPreservesDraft == event \in AuthenticationEvents => form = savedForm
InvNoAutomaticWrite == event \in AuthenticationEvents => writes = writesAtPrompt
InvRoutePreserved == route = "BORROW"
InvSubmissionAuthenticated == event = "SUBMITTED" => authenticated /\ ~expired /\ ~prompt
InvRegistrationDoesNotAuthenticate == event = "REGISTER_SUCCESS" => prompt /\ mode = "REGISTER" /\ ~authenticated
ProbeNotAnonymousPrompt == ~(event = "ANONYMOUS" /\ prompt /\ writes = 0)
ProbeNotExpiredPrompt == ~(event = "EXPIRED" /\ prompt /\ writes = 0)
ProbeNotLoginFailureKeepsDraft == ~(event = "LOGIN_FAILED" /\ prompt /\ form = savedForm)
ProbeNotCancelKeepsDraft == ~(event = "CANCELED" /\ ~prompt /\ form = savedForm)
ProbeNotSuccessWithoutWrite == ~(event = "LOGIN_SUCCESS" /\ ~prompt /\ writes = 0 /\ form = savedForm)
ProbeNotExplicitResubmit == ~(event = "SUBMITTED" /\ writes = 1 /\ authenticated /\ loginCompleted /\ form = savedForm)
ProbeNotRegistrationOpened == ~(event = "REGISTER_OPEN" /\ prompt /\ mode = "REGISTER" /\ form = savedForm)
ProbeNotRegistrationFailureKeepsDraft == ~(event = "REGISTER_FAILED" /\ prompt /\ ~authenticated /\ form = savedForm)
ProbeNotRegisteredWithoutLogin == ~(event = "REGISTER_SUCCESS" /\ registrationCompleted /\ prompt /\ ~authenticated /\ writes = 0)
ProbeNotReturnToLogin == ~(event = "RETURN_LOGIN" /\ registrationCompleted /\ prompt /\ mode = "LOGIN" /\ form = savedForm)
ProbeNotRegisteredThenExplicitResubmit == ~(event = "SUBMITTED" /\ registrationCompleted /\ loginCompleted /\ writes = 1 /\ form = savedForm)
=============================================================================
