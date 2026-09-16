package com.library.catalog.contract;

/**
 * CATALOG 聚合接口：仅供 com.library.bootstrap 装配与一致性检查
 * （MLS-DOC-MD-0002 §3），不得注入应用用例。
 */
public interface CatalogModule extends TitleQuery, TitleCommands, ReadingContentQuery {
}
