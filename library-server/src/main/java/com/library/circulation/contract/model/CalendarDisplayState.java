package com.library.circulation.contract.model;

/** Public daily primary state; copy counts and reader identities stay internal. */
public enum CalendarDisplayState {
    AVAILABLE, RESERVED, LENT;

    public static CalendarDisplayState project(boolean hasAvailable, boolean hasReserved, boolean hasLent) {
        if (hasAvailable) return AVAILABLE;
        if (hasReserved) return RESERVED;
        return hasLent ? LENT : null;
    }
}
