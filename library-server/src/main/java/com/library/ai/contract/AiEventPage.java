package com.library.ai.contract;

import java.util.List;

public record AiEventPage(List<AiEvent> items, String nextCursor) {
    public AiEventPage { items = List.copyOf(items); }
}
