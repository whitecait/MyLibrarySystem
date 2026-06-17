package com.library.service;

import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CategoryService {

    Page<CategoryResponse> getPage(int page, int limit);

    List<CategoryResponse> getList();

    CategoryResponse getInfo(Long id);

    void save(CategoryRequest request);

    void update(Long id, CategoryRequest request);

    void delete(List<Long> ids);
}
