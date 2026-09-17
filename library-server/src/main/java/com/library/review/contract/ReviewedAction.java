package com.library.review.contract;

import com.library.contract.ModuleResult;
import java.util.Map;

public interface ReviewedAction {
    long currentVersion();
    ModuleResult<String> execute(Map<String, Object> payload);
}
