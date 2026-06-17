package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import com.library.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        Page<CategoryResponse> result = categoryService.getPage(page, limit);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @GetMapping("/list")
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(categoryService.getList());
    }

    @GetMapping("/info/{id}")
    public ApiResponse<CategoryResponse> info(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.getInfo(id));
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody CategoryRequest request) {
        categoryService.save(request);
        return ApiResponse.ok();
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestParam Long id, @Valid @RequestBody CategoryRequest request) {
        categoryService.update(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        categoryService.delete(ids);
        return ApiResponse.ok();
    }
}
