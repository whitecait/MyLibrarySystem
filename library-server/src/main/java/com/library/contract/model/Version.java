package com.library.contract.model;

/**
 * 聚合版本（CAS 守卫，DD-0004 §2）。所有可变核心业务行共用同一语义，
 * 故置于共享 contract.model；模块各自的聚合 ID 包装值在所有者模块 contract.model。
 */
public record Version(long value) implements java.io.Serializable {

    public Version {
        if (value < 0) {
            throw new IllegalArgumentException("version 不可为负");
        }
    }

    public static Version of(long value) {
        return new Version(value);
    }

    public Version next() {
        return new Version(value + 1);
    }
}
