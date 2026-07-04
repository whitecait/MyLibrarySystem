package com.library.service;

import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import com.library.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PenaltyService {

    Page<PenaltyResponse> queryPage(int page, int limit, Long readerId, String paid);

    void pay(PayRequest request, User currentUser);

    void delete(List<Long> ids);
}
