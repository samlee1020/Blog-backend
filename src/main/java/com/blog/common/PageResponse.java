package com.blog.common;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long page,
        long size,
        long total,
        long pages
) {
    public static <T> PageResponse<T> of(List<T> items, long page, long size, long total) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResponse<>(items, page, size, total, pages);
    }
}
