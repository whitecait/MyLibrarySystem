package com.library.contract.security;

/**
 * 查询可见性（MLS-DOC-MD-0004 §1）：服务端受控值。
 * PUBLIC 投影只暴露公开字段；LIBRARIAN 可见维护性字段。
 */
public record Visibility(String value) {

    public static final Visibility PUBLIC = new Visibility("PUBLIC");
    public static final Visibility LIBRARIAN = new Visibility("LIBRARIAN");

    public Visibility {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("visibility 值不可为空");
        }
    }
}
