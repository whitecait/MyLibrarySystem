package com.library.service;

import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RenewalService {

    Page<RenewalResponse> queryPage(int page, int limit, Long readerId, String approved);

    void apply(RenewalRequest request);

    void approveBatch(BatchApproval batch);

    void delete(List<Long> ids);
}
