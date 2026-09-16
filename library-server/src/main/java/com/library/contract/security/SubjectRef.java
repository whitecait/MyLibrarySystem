package com.library.contract.security;

/**
 * 服务端受控主体引用：鉴权与归属判定的输入。
 * channel/readerId/onBehalfOf 等请求字段不产生 SubjectRef（MD-0003 §1）。
 */
public record SubjectRef(String type, String id) {

    public static final String ACCOUNT = "ACCOUNT";
    public static final String ANONYMOUS = "ANONYMOUS";
    public static final String SYSTEM = "SYSTEM";
    public static final String PAYMENT_SIMULATOR = "PAYMENT_SIMULATOR";
    public static final String TEST_OPERATOR = "TEST_OPERATOR";

    public SubjectRef {
        if (type == null || type.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("SubjectRef 字段不可为空");
        }
    }

    public static SubjectRef account(String accountId) {
        return new SubjectRef(ACCOUNT, accountId);
    }

    public static SubjectRef anonymous(String clientSessionId) {
        return new SubjectRef(ANONYMOUS, clientSessionId);
    }

    public static SubjectRef system() {
        return new SubjectRef(SYSTEM, "GLOBAL");
    }

    public boolean isAnonymous() {
        return ANONYMOUS.equals(type);
    }
}
