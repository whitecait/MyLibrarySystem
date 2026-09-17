package com.library.ai.contract;

public record VisitorAiOwner(VisitorOwnerDigest digest) implements AiSessionOwner {
    public VisitorAiOwner {
        if (digest == null) throw new IllegalArgumentException("digest 不可为空");
    }
    @Override public String persistenceKey() { return "visitor:" + digest.value(); }
}
