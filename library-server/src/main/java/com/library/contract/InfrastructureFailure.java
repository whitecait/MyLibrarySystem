package com.library.contract;

/**
 * 基础设施失败（MLS-DOC-MD-0004 §1）：模块内部不得抛出 JDBC/Controller/SDK 类型，
 * 一切持久化与外部端点故障统一包装为本异常；应用用例回滚事务，
 * HTTP Adapter 将其映射为 500 信封（MLS-SYS-001）。
 */
public class InfrastructureFailure extends RuntimeException {

    public InfrastructureFailure(String message) {
        super(message);
    }

    public InfrastructureFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
