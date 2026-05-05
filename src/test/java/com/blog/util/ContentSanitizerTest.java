package com.blog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSanitizerTest {
    @Test
    void escapesHtmlInCommentContent() {
        assertThat(ContentSanitizer.cleanComment(" <script>alert(1)</script> "))
                .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
