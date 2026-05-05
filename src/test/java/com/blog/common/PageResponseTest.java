package com.blog.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {
    @Test
    void calculatesPagesFromTotalAndSize() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 2, 2, 5);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.pages()).isEqualTo(3);
    }
}
