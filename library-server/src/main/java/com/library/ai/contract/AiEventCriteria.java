package com.library.ai.contract;

import com.library.contract.model.PageCursor;

public record AiEventCriteria(long sessionId, PageCursor page) {}
