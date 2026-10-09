package com.library.circulation.contract.model;

import java.util.Set;

/** Validates the five pickup blocks offered for new reservations. */
public final class PickupSlot {
    private static final Set<String> ALLOWED_BLOCKS = Set.of(
            "09:00-10:30", "10:30-12:00", "13:00-14:30", "14:30-16:00", "16:00-17:00");

    private PickupSlot() {
    }

    public static boolean isValid(String value) {
        return value != null && ALLOWED_BLOCKS.contains(value);
    }
}
