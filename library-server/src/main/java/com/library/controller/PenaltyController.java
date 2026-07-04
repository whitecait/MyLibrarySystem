package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.PayRequest;
import com.library.dto.PenaltyResponse;
import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.service.PenaltyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/penalties")
public class PenaltyController {

    private final PenaltyService penaltyService;

    public PenaltyController(PenaltyService penaltyService) {
        this.penaltyService = penaltyService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String paid,
            @AuthenticationPrincipal User currentUser) {
        Long readerId = null;
        if (currentUser != null && currentUser.getRole() == UserRole.READER) {
            readerId = currentUser.getReaderId();
        }
        Page<PenaltyResponse> result = penaltyService.queryPage(page, limit, readerId, paid);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @PostMapping("/pay")
    public ApiResponse<Void> pay(@Valid @RequestBody PayRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        penaltyService.pay(request, currentUser);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        penaltyService.delete(ids);
        return ApiResponse.ok();
    }
}
