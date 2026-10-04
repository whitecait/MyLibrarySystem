---- MODULE BusinessReservationMetadata ----
(***************************************************************************)
(* AT-0026 detail metadata contract: write guarantee type from the creation *)
(* source, keep it on waitlist promotion, and preserve bounded slot values.  *)
(* Slot strings are finite boundary samples, not an exhaustive parser model. *)
(* Create actions abstract the write contract; they do not prove SQL storage. *)
(* Legacy rows with no reliable source begin with NoField.                    *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  LegacySlots, HalfHourSamples, InvalidSlotSamples,
  NoField, NoTitle, NoBusinessDate

LegacySlotsExpected == {"AM", "PM"}
HalfHourSamplesExpected == {"00:00-00:30", "12:30-13:00", "23:30-24:00"}
InvalidSlotSamplesExpected == {"00:00-00:29", "00:00-00:31", "12:15-12:45",
                               "23:30-00:00", "24:00-24:30", "not-a-slot"}

LegacyId == "tx-legacy"
DirectId == "9007199254740993"
WaitlistId == "tx-b3"
ReservationIds == {DirectId, WaitlistId, LegacyId}
ValidSlotInputs == LegacySlots \cup HalfHourSamples
AllSlotInputs == ValidSlotInputs \cup InvalidSlotSamples
GuaranteeValues == {NoField, "DIRECT_CONFIRMATION", "WAITLIST"}
Statuses == {"ABSENT", "WAITLIST", "CONFIRMED"}
CopyValues == {NoField, "copy-b2", "copy-b3", "copy-legacy"}
SnapshotValues == {NoField, NoTitle, NoBusinessDate, "title-b2", "title-b3", "title-legacy",
                   "opaque-business-date-b2", "opaque-business-date-b3", "opaque-business-date-legacy",
                   "opaque-reservation-date-b2", "opaque-reservation-date-b3", "opaque-reservation-date-legacy"}
SlotValues == AllSlotInputs \cup {NoField}

MetadataRowSpace ==
  [owner : {"reader-a"}, kind : {"RESERVATION"}, id : ReservationIds,
   status : Statuses, title : SnapshotValues, businessDate : SnapshotValues,
   guaranteeType : GuaranteeValues, reservationDate : SnapshotValues,
   pickupSlot : SlotValues, copyId : CopyValues]

Row(id, status, title, businessDate, guaranteeType, reservationDate, pickupSlot, copyId) ==
  [owner |-> "reader-a", kind |-> "RESERVATION", id |-> id, status |-> status,
   title |-> title, businessDate |-> businessDate, guaranteeType |-> guaranteeType,
   reservationDate |-> reservationDate, pickupSlot |-> pickupSlot, copyId |-> copyId]

EmptyRow(id) == Row(id, "ABSENT", NoTitle, NoBusinessDate, NoField,
                    NoField, NoField, NoField)

DirectRow(slot) == Row(DirectId, "CONFIRMED", "title-b2", "opaque-business-date-b2",
                       "DIRECT_CONFIRMATION", "opaque-reservation-date-b2", slot, "copy-b2")

WaitlistRow(slot) == Row(WaitlistId, "WAITLIST", "title-b3", "opaque-business-date-b3",
                         "WAITLIST", "opaque-reservation-date-b3", slot, NoField)

LegacyRow == Row(LegacyId, "CONFIRMED", "title-legacy", "opaque-business-date-legacy",
                 NoField, "opaque-reservation-date-legacy", "PM", "copy-legacy")

SlotIsValid(slot) == slot \in ValidSlotInputs

SlotEventTag(slot) ==
  IF slot = "AM" THEN "LegacyAMAccepted"
  ELSE IF slot = "PM" THEN "LegacyPMAccepted"
  ELSE IF slot = "00:00-00:30" THEN "FirstHalfHourAccepted"
  ELSE IF slot = "12:30-13:00" THEN "InteriorHalfHourAccepted"
  ELSE IF slot = "23:30-24:00" THEN "LastHalfHourAccepted"
  ELSE IF slot = "00:00-00:29" THEN "Rejected29MinuteSlot"
  ELSE IF slot = "00:00-00:31" THEN "Rejected31MinuteSlot"
  ELSE IF slot = "12:15-12:45" THEN "RejectedOffBoundarySlot"
  ELSE IF slot = "23:30-00:00" THEN "RejectedCrossDaySlot"
  ELSE IF slot = "24:00-24:30" THEN "Rejected24StartSlot"
  ELSE "RejectedMalformedSlot"

PickupEligible(row, slot) == row.status = "CONFIRMED" /\ row.copyId # NoField

VARIABLES records, submittedSlots, tags

vars == <<records, submittedSlots, tags>>

EventTags == {
  "DirectSourceStored", "WaitlistSourceStored", "WaitlistPromotionRetainedSource",
  "SameStatusDifferentSources", "LegacyRowPreserved", "PickupSlotNotGuard",
  "LegacyAMAccepted", "LegacyPMAccepted", "FirstHalfHourAccepted",
  "InteriorHalfHourAccepted", "LastHalfHourAccepted", "Rejected29MinuteSlot",
  "Rejected31MinuteSlot", "RejectedOffBoundarySlot", "RejectedCrossDaySlot",
  "Rejected24StartSlot", "RejectedMalformedSlot"
}

Init ==
  /\ records = [id \in ReservationIds |->
                  IF id = LegacyId THEN LegacyRow ELSE EmptyRow(id)]
  /\ submittedSlots = [id \in ReservationIds |->
                         IF id = LegacyId THEN "PM" ELSE NoField]
  /\ tags = {}

CreateDirect(slot) ==
  /\ slot \in AllSlotInputs
  /\ records[DirectId].status = "ABSENT"
  /\ IF SlotIsValid(slot)
       THEN
         /\ records' = [records EXCEPT ![DirectId] = DirectRow(slot)]
         /\ submittedSlots' = [submittedSlots EXCEPT ![DirectId] = slot]
         /\ tags' = tags \cup {"DirectSourceStored", SlotEventTag(slot)}
       ELSE
         /\ UNCHANGED <<records, submittedSlots>>
         /\ tags' = tags \cup {SlotEventTag(slot)}

CreateWaitlist(slot) ==
  /\ slot \in AllSlotInputs
  /\ records[WaitlistId].status = "ABSENT"
  /\ IF SlotIsValid(slot)
       THEN
         /\ records' = [records EXCEPT ![WaitlistId] = WaitlistRow(slot)]
         /\ submittedSlots' = [submittedSlots EXCEPT ![WaitlistId] = slot]
         /\ tags' = tags \cup {"WaitlistSourceStored", SlotEventTag(slot)}
       ELSE
         /\ UNCHANGED <<records, submittedSlots>>
         /\ tags' = tags \cup {SlotEventTag(slot)}

AssignWaitlist ==
  /\ records[WaitlistId].status = "WAITLIST"
  /\ records' = [records EXCEPT ![WaitlistId].status = "CONFIRMED",
                                  ![WaitlistId].copyId = "copy-b3"]
  /\ tags' = tags \cup {"WaitlistPromotionRetainedSource"}
  /\ UNCHANGED submittedSlots

CheckLegacyRowPreserved ==
  /\ records[LegacyId] = LegacyRow
  /\ submittedSlots[LegacyId] = "PM"
  /\ tags' = tags \cup {"LegacyRowPreserved"}
  /\ UNCHANGED <<records, submittedSlots>>

CheckSameStatusSources ==
  /\ records[DirectId].status = "CONFIRMED"
  /\ records[DirectId].guaranteeType = "DIRECT_CONFIRMATION"
  /\ records[WaitlistId].status = "CONFIRMED"
  /\ records[WaitlistId].guaranteeType = "WAITLIST"
  /\ records[LegacyId].status = "CONFIRMED"
  /\ records[LegacyId].guaranteeType = NoField
  /\ tags' = tags \cup {"SameStatusDifferentSources"}
  /\ UNCHANGED <<records, submittedSlots>>

CheckPickupSlotIndependence ==
  /\ records[DirectId].status = "CONFIRMED"
  /\ records[DirectId].copyId # NoField
  /\ PickupEligible(records[DirectId], "AM")
       = PickupEligible(records[DirectId], "23:30-24:00")
  /\ tags' = tags \cup {"PickupSlotNotGuard"}
  /\ UNCHANGED <<records, submittedSlots>>

Next ==
  \/ \E slot \in AllSlotInputs : CreateDirect(slot)
  \/ \E slot \in AllSlotInputs : CreateWaitlist(slot)
  \/ AssignWaitlist
  \/ CheckLegacyRowPreserved
  \/ CheckSameStatusSources
  \/ CheckPickupSlotIndependence

RecordsTypeOK == records \in [ReservationIds -> MetadataRowSpace]
SubmittedSlotsTypeOK == submittedSlots \in [ReservationIds -> SlotValues]
TagsTypeOK == tags \subseteq EventTags

TypeOK == RecordsTypeOK /\ SubmittedSlotsTypeOK /\ TagsTypeOK

InvSlotInputBoundary ==
  /\ LegacySlots = LegacySlotsExpected
  /\ HalfHourSamples = HalfHourSamplesExpected
  /\ InvalidSlotSamples = InvalidSlotSamplesExpected

InvCreationSourcePersisted ==
  /\ (records[DirectId].status = "ABSENT"
       \/ records[DirectId].guaranteeType = "DIRECT_CONFIRMATION")
  /\ (records[WaitlistId].status = "ABSENT"
       \/ records[WaitlistId].guaranteeType = "WAITLIST")
  /\ (records[WaitlistId].status = "CONFIRMED"
       => records[WaitlistId].guaranteeType = "WAITLIST")

InvAcceptedSlotStoredVerbatim ==
  /\ (records[DirectId].status = "ABSENT"
       \/ (records[DirectId].pickupSlot \in ValidSlotInputs
           /\ records[DirectId].pickupSlot = submittedSlots[DirectId]))
  /\ (records[WaitlistId].status = "ABSENT"
       \/ (records[WaitlistId].pickupSlot \in ValidSlotInputs
           /\ records[WaitlistId].pickupSlot = submittedSlots[WaitlistId]))

InvLegacyRowPreserved ==
  /\ records[LegacyId] = LegacyRow
  /\ submittedSlots[LegacyId] = "PM"

InvPickupEligibilityIgnoresSlot ==
  \A id \in ReservationIds :
    \A first, second \in ValidSlotInputs :
      PickupEligible(records[id], first) = PickupEligible(records[id], second)

ProbeNotDirectSourceStored == "DirectSourceStored" \notin tags
ProbeNotWaitlistPromotionRetainedSource == "WaitlistPromotionRetainedSource" \notin tags
ProbeNotSameStatusDifferentSources == "SameStatusDifferentSources" \notin tags
ProbeNotLegacyRowPreserved == "LegacyRowPreserved" \notin tags
ProbeNotPickupSlotNotGuard == "PickupSlotNotGuard" \notin tags
ProbeNotLegacyAMAccepted == "LegacyAMAccepted" \notin tags
ProbeNotLegacyPMAccepted == "LegacyPMAccepted" \notin tags
ProbeNotFirstHalfHourAccepted == "FirstHalfHourAccepted" \notin tags
ProbeNotInteriorHalfHourAccepted == "InteriorHalfHourAccepted" \notin tags
ProbeNotLastHalfHourAccepted == "LastHalfHourAccepted" \notin tags
ProbeNotRejected29MinuteSlot == "Rejected29MinuteSlot" \notin tags
ProbeNotRejected31MinuteSlot == "Rejected31MinuteSlot" \notin tags
ProbeNotRejectedOffBoundarySlot == "RejectedOffBoundarySlot" \notin tags
ProbeNotRejectedCrossDaySlot == "RejectedCrossDaySlot" \notin tags
ProbeNotRejected24StartSlot == "Rejected24StartSlot" \notin tags
ProbeNotRejectedMalformedSlot == "RejectedMalformedSlot" \notin tags

=============================================================================
