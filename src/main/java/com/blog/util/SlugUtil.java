package com.blog.util;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtil {
    private SlugUtil() {
    }

    public static String from(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? Long.toString(System.currentTimeMillis()) : normalized;
    }
}
