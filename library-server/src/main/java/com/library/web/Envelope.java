package com.library.web;

/**
 * 统一响应信封（MD-0003 §1）：{code,message,data}。
 * code 为模块稳定码（成功形如 MLS-ID-S001，错误形如 MLS-ID-002）。
 */
public record Envelope(String code, String message, Object data) {

    public static Envelope of(String code, String message, Object data) {
        return new Envelope(code, message == null ? "" : message, data);
    }
}
