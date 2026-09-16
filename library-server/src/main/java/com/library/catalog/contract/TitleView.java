package com.library.catalog.contract;

import java.util.List;

/**
 * 书目详情（OpenAPI TitleData：含 ISBN/出版社/出版年/价格/版本/hasReadingContent）。
 */
public record TitleView(long titleId,
                        String title,
                        List<String> authors,
                        String category,
                        String availability,
                        String isbn,
                        String publisher,
                        Integer publishYear,
                        long priceCents,
                        long version,
                        boolean hasReadingContent) {
}
