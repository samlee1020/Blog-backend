package com.blog.util;

import org.springframework.web.util.HtmlUtils;

public final class ContentSanitizer {
    private ContentSanitizer() {
    }

    public static String cleanComment(String content) {
        return HtmlUtils.htmlEscape(content == null ? "" : content.trim());
    }
}
