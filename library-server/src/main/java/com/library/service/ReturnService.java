package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.ReturnRequest;
import com.library.dto.ReturnResponse;
import com.library.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ReturnService {

    Page<ReturnResponse> queryPage(int page, int limit, Long readerId, String approved);

    void apply(ReturnRequest request, User currentUser);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
