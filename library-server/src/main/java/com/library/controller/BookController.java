package com.library.controller;

import com.library.common.ApiResponse;
import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String year) {
        Page<BookResponse> result = bookService.queryBooks(
                page, limit, title, author, publisher, isbn, categoryId, year);
        return ApiResponse.ok(Map.of("list", result.getContent(), "total", result.getTotalElements()));
    }

    @GetMapping("/info/{id}")
    public ApiResponse<BookResponse> info(@PathVariable Long id) {
        return ApiResponse.ok(bookService.getInfo(id));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<BookResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(bookService.getDetail(id));
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody BookRequest request) {
        bookService.save(request);
        return ApiResponse.ok();
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestParam Long id, @Valid @RequestBody BookRequest request) {
        bookService.update(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Long> ids) {
        bookService.delete(ids);
        return ApiResponse.ok();
    }

    // [已停用] 合并入 /books/page — 保留前端兼容性参考
    // @Deprecated
    // @GetMapping("/list")
    // public ApiResponse<Map<String, Object>> list(...) { }

    // [已停用] 旧 MyBatis-Plus 精确匹配查询
    // @Deprecated
    // @GetMapping("/lists")
    // public ApiResponse<?> lists(...) { }

    // [已停用] 旧精确字段单本查询
    // @Deprecated
    // @GetMapping("/query")
    // public ApiResponse<?> query(...) { }
}
