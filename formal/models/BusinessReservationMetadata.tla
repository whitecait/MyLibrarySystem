---- MODULE BusinessReservationMetadata ----
(***************************************************************************)
(* AT-0026 detail metadata contract: write guarantee type from the creation *)
(* source, keep it on waitlist promotion, and preserve bounded slot values.  *)
(* New inputs are the exact five blocks; old period rows remain readable.    *)
(* Create actions abstract the write contract; they do not prove SQL storage. *)
(* Legacy rows with no reliable source begin with NoField.                    *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS
  LegacySlots, PickupBlocks, InvalidSlotSamples,
  NoField, NoTitle, NoBusinessDate

LegacySlotsExpected == {"AM", "PM"}
PickupBlocksExpected == {"09:00-10:30", "10:30-12:00", "13:00-14:30", "14:30-16:00", "16:00-17:00"}
InvalidSlotSamplesExpected == {"AM", "PM", "00:00-00:30", "08:00-09:00", "12:00-13:00", "17:00-18:00", "18:00-19:00", "09:00-09:30", "14:00-14:30", "23:30-00:00", "24:00-24:30", "not-a-slot"}

LegacyId == "tx-legacy"
DirectId == "9007199254740993"
WaitlistId == "tx-b3"
ReservationIds == {DirectId, WaitlistId, LegacyId}
ValidSlotInputs == PickupBlocks
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
  IF slot = "09:00-10:30" THEN "Block1Accepted"
  ELSE IF slot = "10:30-12:00" THEN "Block2Accepted"
  ELSE IF slot = "13:00-14:30" THEN "Block3Accepted"
  ELSE IF slot = "14:30-16:00" THEN "Block4Accepted"
  ELSE IF slot = "16:00-17:00" THEN "Block5Accepted"
  ELSE IF slot \in LegacySlots THEN "RejectedLegacyPeriod"
  ELSE IF slot \in {"00:00-00:30", "08:00-09:00", "17:00-18:00", "18:00-19:00"} THEN "RejectedOutsideOpening"
  ELSE IF slot = "12:00-13:00" THEN "RejectedLunch"
  ELSE IF slot \in {"09:00-09:30", "14:00-14:30"} THEN "RejectedUnlistedOpeningInterval"
  ELSE IF slot \in {"23:30-00:00", "24:00-24:30"} THEN "RejectedCrossDay"
  ELSE "RejectedMalformedSlot"

PickupEligible(row, slot) == row.status = "CONFIRMED" /\ row.copyId # NoField

VARIABLES records, submittedSlots, tags

vars == <<records, submittedSlots, tags>>

EventTags == {"DirectSourceStored", "WaitlistSourceStored", "WaitlistPromotionRetainedSource", "SameStatusDifferentSources", "LegacyRowPreserved", "PickupSlotNotGuard", "Block1Accepted", "Block2Accepted", "Block3Accepted", "Block4Accepted", "Block5Accepted", "RejectedLegacyPeriod", "RejectedOutsideOpening", "RejectedLunch", "RejectedUnlistedOpeningInterval", "RejectedCrossDay", "RejectedMalformedSlot"}

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
  /\ PickupBlocks = PickupBlocksExpected
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
    \A first, second \in (ValidSlotInputs \cup LegacySlots) :
      PickupEligible(records[id], first) = PickupEligible(records[id], second)

ProbeNotDirectSourceStored == "DirectSourceStored" \notin tags
ProbeNotWaitlistPromotionRetainedSource == "WaitlistPromotionRetainedSource" \notin tags
ProbeNotSameStatusDifferentSources == "SameStatusDifferentSources" \notin tags
ProbeNotLegacyRowPreserved == "LegacyRowPreserved" \notin tags
ProbeNotPickupSlotNotGuard == "PickupSlotNotGuard" \notin tags
ProbeNotBlock1Accepted == "Block1Accepted" \notin tags
ProbeNotBlock2Accepted == "Block2Accepted" \notin tags
ProbeNotBlock3Accepted == "Block3Accepted" \notin tags
ProbeNotBlock4Accepted == "Block4Accepted" \notin tags
ProbeNotBlock5Accepted == "Block5Accepted" \notin tags
ProbeNotRejectedLegacyPeriod == "RejectedLegacyPeriod" \notin tags
ProbeNotRejectedOutsideOpening == "RejectedOutsideOpening" \notin tags
ProbeNotRejectedLunch == "RejectedLunch" \notin tags
ProbeNotRejectedUnlistedOpeningInterval == "RejectedUnlistedOpeningInterval" \notin tags
ProbeNotRejectedCrossDay == "RejectedCrossDay" \notin tags
ProbeNotRejectedMalformedSlot == "RejectedMalformedSlot" \notin tags

=============================================================================
