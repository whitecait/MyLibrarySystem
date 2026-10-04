package com.library.circulation.contract.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickupSlotTest {

    @Test
    void acceptsLegacyPeriodsAndAllFortyEightHalfHourIntervals() {
        assertThat(PickupSlot.isValid("AM")).isTrue();
        assertThat(PickupSlot.isValid("PM")).isTrue();

        int accepted = 0;
        for (int start = 0; start < 24 * 60; start += 30) {
            var interval = time(start) + "-" + time(start + 30);
            assertThat(PickupSlot.isValid(interval)).as(interval).isTrue();
            accepted++;
        }

        assertThat(accepted).isEqualTo(48);
        assertThat(PickupSlot.isValid("23:30-24:00")).isTrue();
    }

    @Test
    void rejectsMalformedMisalignedWrongDurationAndCrossDayIntervals() {
        assertThat(PickupSlot.isValid(null)).isFalse();
        for (var invalid : new String[]{
                "", "am", "PM ", "14:15-14:45", "14:00-15:00", "14:00-14:15",
                "23:30-00:00", "24:00-24:30", "24:00-24:00", "00:00-24:00",
                "1:00-01:30", "14:00–14:30", "14:00-14:30 ", "14:00:00-14:30:00",
                "２３:３０-２４:００"}) {
            assertThat(PickupSlot.isValid(invalid)).as(invalid).isFalse();
        }
    }

    private static String time(int minutes) {
        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
    }
}
