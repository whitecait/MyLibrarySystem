package com.library.circulation.contract.model;

/**
 * Creation-time guarantee category for a reservation transaction.
 * The value remains stable when a waitlist entry is later assigned a copy.
 */
public enum GuaranteeType {
    DIRECT_CONFIRMATION,
    WAITLIST
}
