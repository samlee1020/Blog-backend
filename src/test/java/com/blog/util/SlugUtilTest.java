package com.blog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilTest {
    @Test
    void buildsLowercaseDashSlug() {
        assertThat(SlugUtil.from("First Article: Spring Boot!")).isEqualTo("first-article-spring-boot");
    }

    @Test
    void keepsChineseCharacters() {
        assertThat(SlugUtil.from("后端 实现 设计")).isEqualTo("后端-实现-设计");
    }

    @Test
    void returnsFallbackForBlankInput() {
        assertThat(SlugUtil.from("   ")).isEmpty();
    }
}
