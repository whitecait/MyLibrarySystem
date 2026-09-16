package com.library.contract.security;

import java.util.Objects;

/**
 * 服务端受控能力值，取值与 OpenAPI x-permission 一致。
 * 能力判定在服务端完成，与请求携带的字段无关。
 */
public record Capability(String value) {

    public static final Capability PUBLIC = new Capability("PUBLIC");
    public static final Capability AUTHENTICATED = new Capability("AUTHENTICATED");
    public static final Capability AUTHENTICATED_SELF = new Capability("AUTHENTICATED_SELF");
    public static final Capability PUBLIC_OR_AUTHENTICATED = new Capability("PUBLIC_OR_AUTHENTICATED");
    public static final Capability READER = new Capability("READER");
    public static final Capability ADMIN = new Capability("ADMIN");
    public static final Capability LIBRARIAN = new Capability("LIBRARIAN");
    public static final Capability LIBRARIAN_OR_ADMIN = new Capability("LIBRARIAN_OR_ADMIN");
    public static final Capability READER_SELF = new Capability("READER_SELF");
    public static final Capability READER_SELF_OR_LIBRARIAN = new Capability("READER_SELF_OR_LIBRARIAN");
    public static final Capability READER_SELF_OR_LIBRARIAN_OR_SYSTEM = new Capability("READER_SELF_OR_LIBRARIAN_OR_SYSTEM");
    public static final Capability LIBRARIAN_THEN_ADMIN_REVIEW = new Capability("LIBRARIAN_THEN_ADMIN_REVIEW");
    public static final Capability SYSTEM = new Capability("SYSTEM");
    public static final Capability SYSTEM_OR_ADMIN = new Capability("SYSTEM_OR_ADMIN");
    public static final Capability CONTROLLED_TEST_OPERATOR = new Capability("CONTROLLED_TEST_OPERATOR");
    public static final Capability SIMULATOR_CREDENTIAL = new Capability("SIMULATOR_CREDENTIAL");
    public static final Capability SESSION_OWNER = new Capability("SESSION_OWNER");
    public static final Capability SESSION_OWNER_OR_ADMIN = new Capability("SESSION_OWNER_OR_ADMIN");

    public Capability {
        Objects.requireNonNull(value, "capability 值不可为空");
    }
}
