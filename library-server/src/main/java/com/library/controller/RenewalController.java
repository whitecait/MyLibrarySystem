package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BatchApproval;
import com.library.dto.RenewalRequest;
import com.library.dto.RenewalResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.RenewalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/renewals")
public class RenewalController {

    private final RenewalService renewalService;

    public RenewalController(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String approved,
            @AuthenticationPrincipal User currentUser) {
        Long readerId = null;
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<RenewalResponse> result = renewalService.queryPage(page, limit, readerId, approved);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody RenewalRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        renewalService.apply(request, currentUser);
        return ApiResponse.ok();
    }

    @PostMapping("/shBatch")
    public ApiResponse<Void> shBatch(@Valid @RequestBody BatchApproval batch) {
        renewalService.approveBatch(batch);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        renewalService.delete(ids);
        return ApiResponse.ok();
    }
}
