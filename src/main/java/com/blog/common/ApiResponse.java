package com.blog.common;

public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.name(), "success", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.name(), message, null);
    }
}
