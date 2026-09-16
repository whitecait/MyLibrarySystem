package com.library.catalog.contract;

/**
 * 阅读内容（OpenAPI ReadingContentData：contentType/pages/nextCursor）。
 */
public record ReadingContent(String contentType, java.util.List<ReadingPage> pages, String nextCursor) {

    /** 阅读内容页（OpenAPI ReadingPage：pageNo/text）。 */
    public record ReadingPage(int pageNo, String text) {
    }
}
