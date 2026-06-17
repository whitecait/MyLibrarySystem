package com.library.service.impl;

import com.library.dto.CategoryRequest;
import com.library.dto.CategoryResponse;
import com.library.entity.Category;
import com.library.exception.ApiException;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
                               BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    public Page<CategoryResponse> getPage(int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        return categoryRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public List<CategoryResponse> getList() {
        return categoryRepository.findAll(Sort.by("id").ascending())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getInfo(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("类别不存在"));
        return toResponse(category);
    }

    @Override
    public void save(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new ApiException("类别名称已存在");
        }
        Category category = new Category(request.getName());
        categoryRepository.save(category);
    }

    @Override
    public void update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("类别不存在"));
        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new ApiException("类别名称重复");
        }
        category.setName(request.getName());
        categoryRepository.save(category);
    }

    @Override
    public void delete(List<Long> ids) {
        long count = bookRepository.countByCategoryIdIn(ids);
        if (count > 0) {
            throw new ApiException("该类别下存在图书，无法删除");
        }
        categoryRepository.deleteAllById(ids);
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse r = new CategoryResponse();
        r.setId(category.getId());
        r.setName(category.getName());
        r.setAddtime(category.getAddtime() != null ? category.getAddtime().toString() : null);
        return r;
    }
}
