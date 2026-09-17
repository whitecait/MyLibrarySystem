package com.library.ai.contract;

import com.library.contract.model.AccountId;

public record AccountAiOwner(AccountId accountId) implements AiSessionOwner {
    public AccountAiOwner {
        if (accountId == null) throw new IllegalArgumentException("accountId 不可为空");
    }
    @Override public String persistenceKey() { return "account:" + accountId.asString(); }
}
