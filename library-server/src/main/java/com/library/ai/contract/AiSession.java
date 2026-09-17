package com.library.ai.contract;

public record AiSession(long sessionId, String role, Quota quota) {
    public record Quota(int remaining) {}
}
