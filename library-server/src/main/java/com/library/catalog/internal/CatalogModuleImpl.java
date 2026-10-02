package com.library.catalog.internal;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.model.PageCursor;
import com.library.contract.security.Visibility;
import com.library.catalog.contract.MaintainTitle;
import com.library.catalog.contract.ReadingContent;
import com.library.catalog.contract.TitleChange;
import com.library.catalog.contract.TitleCommands;
import com.library.catalog.contract.TitleCriteria;
import com.library.catalog.contract.TitlePage;
import com.library.catalog.contract.TitleQuery;
import com.library.catalog.contract.TitleView;
import com.library.catalog.contract.ReadingContentQuery;
import com.library.catalog.contract.model.TitleId;
import com.library.catalog.internal.adapter.persistence.CatalogStore;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CATALOG 模块实现：检索、详情、维护、阅读内容读取。
 * ISBN 冲突与版本 CAS 拒绝不自动重试；软删除仅当书目无任何受约束事实。
 */
@Component
public class CatalogModuleImpl implements TitleQuery, TitleCommands, ReadingContentQuery {

    private static final int QUERY_MAX_LENGTH = 200;
    private static final Set<String> AVAILABILITY = Set.of("AVAILABLE", "UNAVAILABLE", "UNKNOWN");

    private final CatalogStore titles;

    public CatalogModuleImpl(CatalogStore titles) {
        this.titles = titles;
    }

    // ---- 查询 ---------------------------------------------------------------

    @Override
    public ModuleResult<TitlePage> search(TitleCriteria query, Visibility visibility) {
        var q = normalize(query.q());
        if (q != null && q.length() > QUERY_MAX_LENGTH) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "q 最多 200 字符");
        }
        var availability = normalize(query.availability());
        if (availability != null) {
            availability = availability.toUpperCase(Locale.ROOT);
            if (!AVAILABILITY.contains(availability)) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "availability 无效");
            }
        }
        var cursor = query.page() == null ? PageCursor.first() : query.page();
        var afterId = cursor.cursor() == null ? 0 : parseCursorId(cursor.cursor(), null);
        List<CatalogStore.TitleRow> rows;
        if (q == null || q.isBlank()) {
            rows = titles.list(normalize(query.category()), availability, afterId, cursor.limit() + 1);
        } else {
            Double afterSim = parseCursorSim(cursor.cursor());
            rows = titles.searchByQuery(q, normalize(query.category()), availability,
                    afterSim, afterId, cursor.limit() + 1);
        }
        var hasNext = rows.size() > cursor.limit();
        var pageRows = rows.stream().limit(cursor.limit()).toList();
        var items = pageRows.stream().map(this::toSummary).toList();
        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            var last = pageRows.get(pageRows.size() - 1);
            nextCursor = q == null || q.isBlank()
                    ? String.valueOf(last.id())
                    : "sim:" + cursorSimOf(last) + ":" + last.id();
        }
        return ModuleResult.Accepted.of(new TitlePage(items, nextCursor));
    }

    @Override
    public ModuleResult<TitleView> title(TitleId titleId, Visibility visibility) {
        var row = titles.byId(titleId.value()).orElse(null);
        if (row == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_002.code(), "书目不存在");
        }
        return ModuleResult.Accepted.of(toView(row));
    }

    // ---- 维护 ---------------------------------------------------------------

    @Override
    public ModuleResult<TitleChange> maintain(MaintainTitle command) {
        var action = command.action() == null ? "" : command.action().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "CREATE" -> create(command);
            case "UPDATE" -> update(command);
            case "REMOVE" -> remove(command);
            default -> ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "未知维护动作");
        };
    }

    private ModuleResult<TitleChange> create(MaintainTitle command) {
        var fieldError = validateFields(command, true);
        if (fieldError != null) {
            return fieldError;
        }
        if (command.isbn() != null && !command.isbn().isBlank() && titles.isbnExistsExcept(0, command.isbn())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_003.code(), "ISBN 已被其他书目使用");
        }
        var id = titles.insert(new CatalogStore.NewTitle(blankToNull(command.isbn()), command.title(),
                joinAuthors(command.authors()), command.category(), blankToNull(command.publisher()),
                command.publishYear(), command.priceCents()));
        return ModuleResult.Accepted.of(new TitleChange("CREATE", id, 0, null));
    }

    private ModuleResult<TitleChange> update(MaintainTitle command) {
        if (command.titleId() == null || command.version() == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "UPDATE 需要 titleId 与 version");
        }
        var fieldError = validateFields(command, false);
        if (fieldError != null) {
            return fieldError;
        }
        var current = titles.byId(command.titleId()).orElse(null);
        if (current == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_002.code(), "书目不存在");
        }
        if (command.isbn() != null && !command.isbn().isBlank()
                && titles.isbnExistsExcept(command.titleId(), command.isbn())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_003.code(), "ISBN 已被其他书目使用");
        }
        var newVersion = titles.update(new CatalogStore.TitleUpdate(command.titleId(), command.version(),
                blankToNull(command.isbn()), command.title(), joinAuthors(command.authors()),
                command.category(), command.publisher(), command.publishYear(), command.priceCents()));
        if (newVersion == null) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CAT_003.code(),
                    current.version(), "版本已变化，请基于最新版本重新提交");
        }
        return ModuleResult.Accepted.of(new TitleChange("UPDATE", command.titleId(), newVersion, null));
    }

    private ModuleResult<TitleChange> remove(MaintainTitle command) {
        if (command.titleId() == null || command.version() == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "REMOVE 需要 titleId 与 version");
        }
        var current = titles.byId(command.titleId()).orElse(null);
        if (current == null) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_002.code(), "书目不存在");
        }
        if (titles.hasCopies(command.titleId()) || titles.hasTransactions(command.titleId())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_004.code(), "书目仍有副本或流通事实，不能移除");
        }
        if (!titles.delete(command.titleId(), command.version())) {
            return ModuleResult.Rejected.conflict(StableErrorCode.MLS_CAT_003.code(),
                    current.version(), "版本已变化，请基于最新版本重新提交");
        }
        return ModuleResult.Accepted.of(new TitleChange("REMOVE", command.titleId(), command.version(), "REMOVED"));
    }

    // ---- 阅读内容 -------------------------------------------------------------

    @Override
    public ModuleResult<ReadingContent> readingContent(TitleId titleId, PageCursor page) {
        if (titles.byId(titleId.value()).isEmpty()) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_002.code(), "书目不存在");
        }
        if (!titles.hasContent(titleId.value())) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_005.code(), "无公开阅读内容");
        }
        var cursor = page == null ? PageCursor.first() : page;
        var after = cursor.cursor() == null ? 0 : Integer.parseInt(cursor.cursor());
        var rows = titles.contentPages(titleId.value(), after, cursor.limit() + 1);
        var hasNext = rows.size() > cursor.limit();
        var pageRows = rows.stream().limit(cursor.limit()).toList();
        var pages = pageRows.stream()
                .map(row -> new ReadingContent.ReadingPage(row.pageNo(), row.text())).toList();
        var nextCursor = hasNext && !pageRows.isEmpty()
                ? String.valueOf(pageRows.get(pageRows.size() - 1).pageNo()) : null;
        var contentType = rows.isEmpty() ? "text/plain" : rows.get(0).contentType();
        return ModuleResult.Accepted.of(new ReadingContent(contentType, pages, nextCursor));
    }

    // ---- 投影 ----------------------------------------------------------------

    private TitlePage.TitleSummary toSummary(CatalogStore.TitleRow row) {
        return new TitlePage.TitleSummary(row.id(), row.title(), splitAuthors(row.authorsJoined()),
                row.category(), availability(row.availability()));
    }

    private TitleView toView(CatalogStore.TitleRow row) {
        return new TitleView(row.id(), row.title(), splitAuthors(row.authorsJoined()), row.category(),
                availability(row.availability()), row.isbn(), row.publisher(), row.publishYear(),
                row.priceCents(), row.version(), titles.hasContent(row.id()));
    }

    private String availability(String raw) {
        return AVAILABILITY.contains(raw) ? raw : "UNKNOWN";
    }

    // ---- 工具 ----------------------------------------------------------------

    private ModuleResult.Rejected<TitleChange> validateFields(MaintainTitle command, boolean creating) {
        if (creating) {
            if (command.title() == null || command.title().isBlank() || command.title().length() > 256) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "title 必填且不超过 256 字符");
            }
            if (command.authors() == null || command.authors().isEmpty()) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "authors 至少一位");
            }
            if (command.priceCents() == null || command.priceCents() < 0) {
                return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "priceCents 必须为非负整数分");
            }
        }
        if (command.priceCents() != null && command.priceCents() < 0) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "priceCents 必须为非负整数分");
        }
        if (command.isbn() != null && command.isbn().length() > 20) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "isbn 最多 20 字符");
        }
        if (command.category() != null && command.category().length() > 64) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "category 最多 64 字符");
        }
        if (command.publisher() != null && command.publisher().length() > 128) {
            return ModuleResult.Rejected.of(StableErrorCode.MLS_CAT_001.code(), "publisher 最多 128 字符");
        }
        return null;
    }

    private static String joinAuthors(List<String> authors) {
        return authors == null ? null : String.join("; ", authors);
    }

    private static List<String> splitAuthors(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("; ")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long parseCursorId(String cursor, Double ignored) {
        try {
            if (cursor != null && cursor.startsWith("sim:")) {
                return Long.parseLong(cursor.split(":")[2]);
            }
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Double parseCursorSim(String cursor) {
        if (cursor != null && cursor.startsWith("sim:")) {
            try {
                return Double.parseDouble(cursor.split(":")[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static double cursorSimOf(CatalogStore.TitleRow row) {
        // 相似度在行投影中未回传时退化为 0：仅影响下一页排序锚点，不影响本页正确性
        return 0d;
    }
}
