package com.library.catalog;

import com.library.contract.InfrastructureFailure;
import com.library.contract.ModuleResult;
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
import com.library.catalog.contract.model.TitleId;
import com.library.catalog.internal.CatalogModuleImpl;
import com.library.catalog.internal.adapter.persistence.InMemoryCatalogStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UT-CAT-001~005（MLS-DOC-MD-0005 §3）：查询解析、书目维护规则、公开投影。
 * pg_trgm 排序性能不在本层证明（IT-CAT-001 真库）。
 */
class CatalogModuleTest {

    private final InMemoryCatalogStore store = new InMemoryCatalogStore();
    private final CatalogModuleImpl module = new CatalogModuleImpl(store);

    private long createSample(String isbn, String title) {
        var change = ((ModuleResult.Accepted<TitleChange>) module.maintain(new MaintainTitle(
                "CREATE", null, isbn, title, List.of("作者甲"), "计算机", "出版社", 2020, 9900L, null, null)))
                .value();
        return change.titleId();
    }

    // UT-CAT-001 查询解析：q 超长拒绝；limit 越界为基础设施错误
    @Test
    void utCat001_queryParsing() {
        assertThat(codeOf(module.search(new TitleCriteria("x".repeat(201), null, PageCursor.first()),
                Visibility.PUBLIC))).isEqualTo("MLS-CAT-001");
        assertThatThrownBy(() -> module.search(new TitleCriteria("ok", null, new PageCursor(null, 0)),
                Visibility.PUBLIC)).isInstanceOf(InfrastructureFailure.class);
        var page = (ModuleResult.Accepted<TitlePage>) module.search(
                new TitleCriteria(null, null, PageCursor.of(null, 3)), Visibility.PUBLIC);
        assertThat(page.value().items()).isEmpty();
        assertThat(page.value().nextCursor()).isNull();
        assertThat(codeOf(module.search(new TitleCriteria(null, null, "BROKEN", PageCursor.first()),
                Visibility.PUBLIC))).isEqualTo("MLS-CAT-001");
    }

    // UT-CAT-002 书目维护 CREATE：成功投影、字段守卫、ISBN 冲突
    @Test
    void utCat002_createRulesAndPublicProjection() {
        var titleId = createSample("978-7-111", "数据库系统概念");
        var view = (ModuleResult.Accepted<TitleView>) module.title(TitleId.of(titleId), Visibility.PUBLIC);
        assertThat(view.value().title()).isEqualTo("数据库系统概念");
        assertThat(view.value().authors()).containsExactly("作者甲");
        assertThat(view.value().availability()).isEqualTo("UNKNOWN"); // 无副本事实
        assertThat(view.value().hasReadingContent()).isFalse();
        assertThat(view.value().version()).isZero();

        assertThat(codeOf(module.maintain(new MaintainTitle("CREATE", null, "978-7-111",
                "另书", List.of("乙"), "计算机", null, null, 100L, null, null)))).isEqualTo("MLS-CAT-003");
        assertThat(codeOf(module.maintain(new MaintainTitle("CREATE", null, "isbn-b",
                "", List.of("乙"), "计算机", null, null, 100L, null, null)))).isEqualTo("MLS-CAT-001");
        assertThat(codeOf(module.maintain(new MaintainTitle("CREATE", null, "isbn-c",
                "无作者书", List.of(), "计算机", null, null, 100L, null, null)))).isEqualTo("MLS-CAT-001");

        var summary = (ModuleResult.Accepted<TitlePage>) module.search(
                new TitleCriteria(null, null, PageCursor.first()), Visibility.PUBLIC);
        assertThat(summary.value().items()).anyMatch(s -> s.titleId() == titleId);
    }

    // UT-CAT-003 UPDATE CAS：版本冲突携带 currentVersion、成功推进版本
    @Test
    void utCat003_updateVersionCompetition() {
        var titleId = createSample("isbn-u1", "重构");
        var accepted = (ModuleResult.Accepted<TitleChange>) module.maintain(new MaintainTitle(
                "UPDATE", titleId, null, "重构（第2版）", null, null, null, null, null, 0L, null));
        assertThat(accepted.value().version()).isEqualTo(1);

        var stale = (ModuleResult.Rejected<TitleChange>) module.maintain(new MaintainTitle(
                "UPDATE", titleId, null, "并发改", null, null, null, null, null, 0L, null));
        assertThat(stale.code()).isEqualTo("MLS-CAT-003");
        assertThat(stale.currentVersion()).isEqualTo(1L);
    }

    // UT-CAT-004 REMOVE：受约束事实拒绝、成功移除后不可见
    @Test
    void utCat004_removeConstraints() {
        var blocked = createSample("isbn-r1", "有副本的书");
        store.addCopy(blocked);
        assertThat(codeOf(module.maintain(new MaintainTitle("REMOVE", blocked, null, null, null,
                null, null, null, null, 0L, "尝试移除")))).isEqualTo("MLS-CAT-004");

        var blockedByTx = createSample("isbn-r2", "有流通的书");
        store.addTransaction(blockedByTx);
        assertThat(codeOf(module.maintain(new MaintainTitle("REMOVE", blockedByTx, null, null, null,
                null, null, null, null, 0L, "尝试移除")))).isEqualTo("MLS-CAT-004");

        var free = createSample("isbn-r3", "干净的书");
        var removed = (ModuleResult.Accepted<TitleChange>) module.maintain(new MaintainTitle(
                "REMOVE", free, null, null, null, null, null, null, null, 0L, "无事实移除"));
        assertThat(removed.value().status()).isEqualTo("REMOVED");
        assertThat(codeOf(module.title(TitleId.of(free), Visibility.PUBLIC))).isEqualTo("MLS-CAT-002");
    }

    // UT-CAT-005 阅读内容：无内容拒绝、分页游标、书目不存在
    @Test
    void utCat005_readingContentPaging() {
        var titleId = createSample("isbn-c1", "有内容的书");
        store.addContentPage(titleId, 1, "text/plain", "第一页");
        store.addContentPage(titleId, 2, "text/plain", "第二页");

        var page1 = (ModuleResult.Accepted<ReadingContent>) module.readingContent(
                TitleId.of(titleId), PageCursor.of(null, 1));
        assertThat(page1.value().pages()).hasSize(1);
        assertThat(page1.value().pages().get(0).pageNo()).isEqualTo(1);
        assertThat(page1.value().nextCursor()).isEqualTo("1");

        var page2 = (ModuleResult.Accepted<ReadingContent>) module.readingContent(
                TitleId.of(titleId), PageCursor.of(page1.value().nextCursor(), 1));
        assertThat(page2.value().pages().get(0).pageNo()).isEqualTo(2);
        assertThat(page2.value().nextCursor()).isNull();

        var bare = createSample("isbn-c2", "无内容的书");
        assertThat(codeOf(module.readingContent(TitleId.of(bare), PageCursor.first())))
                .isEqualTo("MLS-CAT-005");
        assertThat(codeOf(module.readingContent(TitleId.of(999999), PageCursor.first())))
                .isEqualTo("MLS-CAT-002");
    }

    private String codeOf(ModuleResult<?> result) {
        if (result instanceof ModuleResult.Rejected<?> rejected) {
            return rejected.code();
        }
        throw new AssertionError("期望业务拒绝，实际为 Accepted");
    }
}
