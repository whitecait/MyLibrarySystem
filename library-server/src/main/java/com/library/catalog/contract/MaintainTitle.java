package com.library.catalog.contract;

import java.util.List;

/**
 * 书目维护命令（OpenAPI createTitle/updateTitle/removeTitle 的统一承载）。
 * action ∈ CREATE|UPDATE|REMOVE；UPDATE/REMOVE 携带 version 做 CAS。
 */
public record MaintainTitle(String action,
                            Long titleId,
                            String isbn,
                            String title,
                            List<String> authors,
                            String category,
                            String publisher,
                            Integer publishYear,
                            Long priceCents,
                            Long version,
                            String reason) {
}
