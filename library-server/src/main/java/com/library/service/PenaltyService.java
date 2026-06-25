package com.library.service;

import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PenaltyService {

    Page<PenaltyResponse> queryPage(int page, int limit, Long readerId, String paid);

    void pay(PayRequest request);

    void delete(List<Long> ids);
}
