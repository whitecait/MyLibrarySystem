package com.library.identity.contract;

import com.library.contract.model.AccountId;

/** Minimal account state used to revalidate a pending account review. */
public record AccountState(AccountId accountId, String role, String status, long version) {
}
