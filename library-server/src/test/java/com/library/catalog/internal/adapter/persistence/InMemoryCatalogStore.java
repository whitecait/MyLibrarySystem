package com.library.catalog.internal.adapter.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用 CatalogStore Adapter（单元层）：检索/维护/内容页的接口语义内存实现。
 * pg_trgm 相似度排序语义由 IT-CAT-001 真库证明，此处按包含匹配近似。
 */
public class InMemoryCatalogStore implements CatalogStore {

    private final Map<Long, TitleRow> titles = new HashMap<>();
    private final Map<Long, List<ContentPageRow>> content = new HashMap<>();
    private final Map<Long, Integer> copyCount = new HashMap<>();
    private final Map<Long, Integer> transactionCount = new HashMap<>();
    private long sequence;

    /** 单元测试直造副本事实（移除守卫用）。 */
    public void addCopy(long titleId) {
        copyCount.merge(titleId, 1, Integer::sum);
    }

    /** 单元测试直造流通事实（移除守卫用）。 */
    public void addTransaction(long titleId) {
        transactionCount.merge(titleId, 1, Integer::sum);
    }

    public void addContentPage(long titleId, int pageNo, String contentType, String text) {
        content.computeIfAbsent(titleId, k -> new ArrayList<>())
                .add(new ContentPageRow(pageNo, contentType, text));
    }

    @Override
    public Optional<TitleRow> byId(long titleId) {
        return Optional.ofNullable(titles.get(titleId));
    }

    @Override
    public boolean isbnExistsExcept(long titleId, String isbn) {
        return titles.values().stream()
                .anyMatch(t -> isbn != null && isbn.equals(t.isbn()) && t.id() != titleId);
    }

    @Override
    public List<TitleRow> searchByQuery(String q, String category, Double afterSimilarity,
                                        long afterId, int limit) {
        return titles.values().stream()
                .filter(t -> t.title().contains(q) || (t.authorsJoined() != null && t.authorsJoined().contains(q)))
                .filter(t -> category == null || category.equals(t.category()))
                .filter(t -> afterSimilarity == null || t.id() > afterId)
                .sorted(java.util.Comparator.comparingLong(TitleRow::id))
                .limit(limit)
                .toList();
    }

    @Override
    public List<TitleRow> list(String category, long afterId, int limit) {
        return titles.values().stream()
                .filter(t -> t.id() > afterId)
                .filter(t -> category == null || category.equals(t.category()))
                .sorted(java.util.Comparator.comparingLong(TitleRow::id))
                .limit(limit)
                .toList();
    }

    @Override
    public long insert(NewTitle candidate) {
        var id = ++sequence;
        titles.put(id, new TitleRow(id, candidate.title(), candidate.authorsJoined(), candidate.publisher(),
                candidate.category(), candidate.isbn(), candidate.publishYear(), candidate.priceCents(),
                0, availabilityOf(id)));
        return id;
    }

    @Override
    public Long update(TitleUpdate u) {
        var current = titles.get(u.titleId());
        if (current == null || current.version() != u.version()) {
            return null;
        }
        var updated = new TitleRow(current.id(),
                u.title() != null ? u.title() : current.title(),
                u.authorsJoined() != null ? u.authorsJoined() : current.authorsJoined(),
                u.publisher() != null ? u.publisher() : current.publisher(),
                u.category() != null ? u.category() : current.category(),
                u.isbn() != null ? u.isbn() : current.isbn(),
                u.publishYear() != null ? u.publishYear() : current.publishYear(),
                u.priceCents() != null ? u.priceCents() : current.priceCents(),
                current.version() + 1, availabilityOf(current.id()));
        titles.put(updated.id(), updated);
        return updated.version();
    }

    @Override
    public boolean delete(long titleId, long version) {
        var current = titles.get(titleId);
        if (current == null || current.version() != version) {
            return false;
        }
        titles.remove(titleId);
        return true;
    }

    @Override
    public boolean hasCopies(long titleId) {
        return copyCount.getOrDefault(titleId, 0) > 0;
    }

    @Override
    public boolean hasTransactions(long titleId) {
        return transactionCount.getOrDefault(titleId, 0) > 0;
    }

    @Override
    public boolean hasContent(long titleId) {
        return content.containsKey(titleId) && !content.get(titleId).isEmpty();
    }

    @Override
    public List<ContentPageRow> contentPages(long titleId, int afterPageNo, int limit) {
        return content.getOrDefault(titleId, List.of()).stream()
                .filter(p -> p.pageNo() > afterPageNo)
                .sorted(java.util.Comparator.comparingInt(ContentPageRow::pageNo))
                .limit(limit)
                .toList();
    }

    private String availabilityOf(long titleId) {
        var copies = copyCount.getOrDefault(titleId, 0);
        if (copies == 0) {
            return "UNKNOWN";
        }
        // 内存近似：存在副本即视为 AVAILABLE（真库按 SHELF_READY 聚合，IT-CAT 证明）
        return "AVAILABLE";
    }
}
