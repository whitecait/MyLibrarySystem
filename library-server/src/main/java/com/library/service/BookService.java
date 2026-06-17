package com.library.service;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BookService {

    Page<BookResponse> queryBooks(int page, int limit,
            String title, String author, String publisher,
            String isbn, Long categoryId, String year);

    BookResponse getInfo(Long id);

    BookResponse getDetail(Long id);

    void save(BookRequest request);

    void update(Long id, BookRequest request);

    void delete(List<Long> ids);
}
