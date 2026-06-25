package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BorrowService {

    Page<BorrowResponse> queryPage(int page, int limit, Long readerId, Long bookId, String approved);

    void apply(BorrowRequest request, User currentUser);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
