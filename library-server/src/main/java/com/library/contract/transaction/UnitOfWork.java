package com.library.contract.transaction;

import java.util.function.Supplier;

/**
 * 应用层事务边界（MLS-DOC-MD-0004 §4）：只控制提交/回滚，
 * 事务上下文向模块调用传播；模块不得自行提交。
 * 实现基于线程绑定 JDBC 事务（PROPAGATION_REQUIRED，嵌套调用加入同一事务）。
 */
public interface UnitOfWork {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
