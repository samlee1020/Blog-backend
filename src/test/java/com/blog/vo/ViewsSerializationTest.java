package com.blog.vo;

import com.blog.domain.UserRole;
import com.blog.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ViewsSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void loginUserRoundTripsForRedisTokenStorage() throws Exception {
        LoginUser user = new LoginUser(1L, "admin", "Admin", UserRole.ADMIN, LocalDateTime.of(2026, 5, 4, 10, 0), "token");

        LoginUser restored = objectMapper.readValue(objectMapper.writeValueAsString(user), LoginUser.class);

        assertThat(restored.userId()).isEqualTo(1L);
        assertThat(restored.role()).isEqualTo(UserRole.ADMIN);
        assertThat(restored.token()).isEqualTo("token");
    }
}
