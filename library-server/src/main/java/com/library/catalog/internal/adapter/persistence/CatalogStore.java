package com.library.catalog.internal.adapter.persistence;

import java.util.List;
import java.util.Optional;

/**
 * CATALOG 持久化内部缝（MLS-DOC-MD-0004 §4）：pg_trgm 检索、书目版本条件更新、
 * 阅读内容分页。availability 为书目级只读投影（按副本存在性聚合），
 * 不对外暴露逐册状态；副本状态所有权仍在 CIRCULATION。
 */
public interface CatalogStore {

    Optional<TitleRow> byId(long titleId);

    boolean isbnExistsExcept(long titleId, String isbn);

    /** pg_trgm 相似度检索：q 非空时按 similarity DESC、id ASC 键集分页。 */
    List<TitleRow> searchByQuery(String q, String category, Double afterSimilarity, long afterId, int limit);

    /** 无检索词：category 过滤 + id 键集分页。 */
    List<TitleRow> list(String category, long afterId, int limit);

    long insert(NewTitle candidate);

    /** CAS 更新；版本不匹配返回 null（BR-0045 冲突，不自动重试判定）。 */
    Long update(TitleUpdate update);

    /** CAS 删除；版本不匹配返回 false。 */
    boolean delete(long titleId, long version);

    boolean hasCopies(long titleId);

    boolean hasTransactions(long titleId);

    boolean hasContent(long titleId);

    List<ContentPageRow> contentPages(long titleId, int afterPageNo, int limit);

    record TitleRow(long id, String title, String authorsJoined, String publisher, String category,
                    String isbn, Integer publishYear, long priceCents, long version,
                    String availability) {
    }

    record NewTitle(String isbn, String title, String authorsJoined, String category,
                    String publisher, Integer publishYear, long priceCents) {
    }

    record TitleUpdate(long titleId, long version, String isbn, String title, String authorsJoined,
                       String category, String publisher, Integer publishYear, Long priceCents) {
    }

    record ContentPageRow(int pageNo, String contentType, String text) {
    }
}
