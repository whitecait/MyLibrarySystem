package com.library.circulation.contract.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickupSlotTest {
    @Test
    void acceptsTheFiveOpeningHourPickupBlocks() {
        for (var slot : new String[]{
                "09:00-10:30", "10:30-12:00", "13:00-14:30", "14:30-16:00", "16:00-17:00"}) {
            assertThat(PickupSlot.isValid(slot)).as(slot).isTrue();
        }
    }

    @Test
    void rejectsClosedLunchBufferLegacyAndUnlistedInputs() {
        assertThat(PickupSlot.isValid(null)).isFalse();
        for (var invalid : new String[]{
                "", "AM", "PM", "00:00-00:30", "08:00-09:00", "12:00-13:00",
                "12:30-13:00", "17:00-18:00", "18:00-19:00", "23:30-24:00",
                "09:00-09:30", "14:00-14:30", "09:00-12:00", "14:15-14:45",
                "23:30-00:00", "24:00-24:30", "09:00–10:30", "09:00-10:30 ",
                "9:00-10:30", "０９:００-１０:３０"}) {
            assertThat(PickupSlot.isValid(invalid)).as(invalid).isFalse();
        }
    }
}
