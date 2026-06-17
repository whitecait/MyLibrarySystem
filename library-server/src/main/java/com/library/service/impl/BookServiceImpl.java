package com.library.service.impl;

import com.library.dto.BookRequest;
import com.library.dto.BookResponse;
import com.library.entity.Book;
import com.library.entity.Category;
import com.library.exception.ApiException;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRepository;
import com.library.repository.CategoryRepository;
import com.library.service.BookService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BorrowRepository borrowRepository;

    public BookServiceImpl(BookRepository bookRepository,
                           CategoryRepository categoryRepository,
                           BorrowRepository borrowRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.borrowRepository = borrowRepository;
    }

    @Override
    public Page<BookResponse> queryBooks(int page, int limit,
            String title, String author, String publisher,
            String isbn, Long categoryId, String year) {
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("id").descending());
        Specification<Book> spec = buildSpec(title, author, publisher, isbn, categoryId, year);
        Page<Book> bookPage = bookRepository.findAll(spec, pageable);

        // Batch lookup category names (avoid N+1)
        List<Long> categoryIds = bookPage.getContent().stream()
                .map(Book::getCategoryId)
                .filter(cid -> cid != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> categoryNameMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return bookPage.map(book -> toResponse(book, categoryNameMap));
    }

    @Override
    public BookResponse getInfo(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ApiException("图书不存在"));
        String categoryName = null;
        if (book.getCategoryId() != null) {
            categoryName = categoryRepository.findById(book.getCategoryId())
                    .map(Category::getName).orElse(null);
        }
        return toResponse(book, categoryName);
    }

    @Override
    public BookResponse getDetail(Long id) {
        return getInfo(id); // same logic, different auth level in SecurityConfig
    }

    @Override
    public void save(BookRequest request) {
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            throw new ApiException("图书编号已存在");
        }
        Book book = new Book();
        applyRequest(book, request);
        bookRepository.save(book);
    }

    @Override
    public void update(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ApiException("图书不存在"));
        if (bookRepository.existsByIsbnAndIdNot(request.getIsbn(), id)) {
            throw new ApiException("图书编号重复");
        }
        applyRequest(book, request);
        bookRepository.save(book);
    }

    @Override
    public void delete(List<Long> ids) {
        long count = borrowRepository.countByBookIdIn(ids);
        if (count > 0) {
            throw new ApiException("该图书存在借阅记录，无法删除");
        }
        bookRepository.deleteAllById(ids);
    }

    // ---- private helpers ----

    private Specification<Book> buildSpec(String title, String author,
            String publisher, String isbn, Long categoryId, String year) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(title)) {
                predicates.add(cb.like(root.get("title"), "%" + title + "%"));
            }
            if (StringUtils.hasText(author)) {
                predicates.add(cb.like(root.get("author"), "%" + author + "%"));
            }
            if (StringUtils.hasText(publisher)) {
                predicates.add(cb.like(root.get("publisher"), "%" + publisher + "%"));
            }
            if (StringUtils.hasText(isbn)) {
                predicates.add(cb.like(root.get("isbn"), "%" + isbn + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (StringUtils.hasText(year)) {
                predicates.add(cb.equal(root.get("year"), year));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private BookResponse toResponse(Book book, Map<Long, String> categoryNameMap) {
        String categoryName = null;
        if (book.getCategoryId() != null) {
            categoryName = categoryNameMap.get(book.getCategoryId());
        }
        return toResponse(book, categoryName);
    }

    private BookResponse toResponse(Book book, String categoryName) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setIsbn(book.getIsbn());
        r.setTitle(book.getTitle());
        r.setAuthor(book.getAuthor());
        r.setPublisher(book.getPublisher());
        r.setYear(book.getYear());
        r.setCoverImage(book.getCoverImage());
        r.setStock(book.getStock());
        r.setDescription(book.getDescription());
        r.setCategoryId(book.getCategoryId());
        r.setCategoryName(categoryName);
        r.setAddtime(book.getAddtime() != null ? book.getAddtime().toString() : null);
        return r;
    }

    private void applyRequest(Book book, BookRequest req) {
        book.setIsbn(req.getIsbn());
        book.setTitle(req.getTitle());
        book.setAuthor(req.getAuthor());
        book.setPublisher(req.getPublisher());
        book.setYear(req.getYear());
        book.setCoverImage(req.getCoverImage());
        book.setStock(req.getStock() != null ? req.getStock() : 1);
        book.setDescription(req.getDescription());
        book.setCategoryId(req.getCategoryId());
    }
}
