package com.library.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified API response wrapper.
 * <p>
 * Format follows the old frontend convention: {@code code: 0} means success.
 *
 * @param <T> data payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    private ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(0, msg, data);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(1, msg, null);
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    // ---- getters ----

    public int getCode() { return code; }

    public String getMsg() { return msg; }

    public T getData() { return data; }

    // ---- setters (for Jackson) ----

    public void setCode(int code) { this.code = code; }

    public void setMsg(String msg) { this.msg = msg; }

    public void setData(T data) { this.data = data; }
}
