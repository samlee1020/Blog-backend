package com.blog.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void successUsesUnifiedShape() {
        ApiResponse<Boolean> response = ApiResponse.success(true);

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isTrue();
    }
}
