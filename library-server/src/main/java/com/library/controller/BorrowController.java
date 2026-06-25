package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BatchApproval;
import com.library.dto.BorrowRequest;
import com.library.dto.BorrowResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.BorrowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/borrows")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long readerId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String approved,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<BorrowResponse> result = borrowService.queryPage(page, limit, readerId, bookId, approved);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@Valid @RequestBody BorrowRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        borrowService.apply(request, currentUser);
        return ApiResponse.ok();
    }

    @PostMapping("/shBatch")
    public ApiResponse<Void> shBatch(@Valid @RequestBody BatchApproval batch) {
        borrowService.approveBatch(batch);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        borrowService.delete(ids);
        return ApiResponse.ok();
    }
}
