package com.library.circulation.contract.model;

import java.util.regex.Pattern;

/** Validates legacy pickup periods and concrete same-day half-hour intervals. */
public final class PickupSlot {

    private static final Pattern HALF_HOUR_INTERVAL = Pattern.compile(
            "^(?:(?:[01][0-9]|2[0-3]):(?:00|30)-(?:[01][0-9]|2[0-3]):(?:00|30)|23:30-24:00)$");

    private PickupSlot() {
    }

    /**
     * Accepts the legacy AM/PM values or one exact ASCII interval on a 30-minute boundary.
     * 24:00 is valid only as the end of 23:30-24:00.
     */
    public static boolean isValid(String value) {
        if ("AM".equals(value) || "PM".equals(value)) {
            return true;
        }
        if (value == null || !HALF_HOUR_INTERVAL.matcher(value).matches()) {
            return false;
        }

        int start = minutesOfDay(value.substring(0, 5));
        var endText = value.substring(6);
        int end = "24:00".equals(endText) ? 24 * 60 : minutesOfDay(endText);
        return end == start + 30;
    }

    private static int minutesOfDay(String time) {
        int hour = Integer.parseInt(time.substring(0, 2));
        int minute = Integer.parseInt(time.substring(3, 5));
        return hour * 60 + minute;
    }
}
