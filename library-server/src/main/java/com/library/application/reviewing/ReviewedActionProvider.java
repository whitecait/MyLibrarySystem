package com.library.application.reviewing;

import com.library.review.contract.ReviewQuery;
import com.library.review.contract.ReviewedAction;

public interface ReviewedActionProvider { ReviewedAction actionFor(ReviewQuery.ReviewDetail detail); }
