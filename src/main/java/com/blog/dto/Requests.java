package com.blog.dto;

import com.blog.domain.ArticleStatus;
import com.blog.domain.CommentStatus;
import com.blog.domain.ProjectStatus;
import com.blog.domain.UserStatus;
import com.blog.domain.ValueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Requests {
    private Requests() {
    }

    public record GuestRegisterRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @Size(min = 1, max = 64) String nickname
    ) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ArticleRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String slug,
            @Size(max = 500) String summary,
            @Size(max = 500) String coverImageUrl,
            @NotBlank String contentMarkdown,
            Long categoryId,
            List<Long> tagIds,
            ArticleStatus status
    ) {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String slug,
            @Size(max = 1000) String description,
            String contentMarkdown,
            @Size(max = 500) String imageUrl,
            @Size(max = 500) String projectUrl,
            @Size(max = 30) List<@Size(max = 64) String> tags,
            Integer sortOrder,
            ProjectStatus status
    ) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 100) String slug,
            @Size(max = 255) String description,
            Integer sortOrder
    ) {
    }

    public record TagRequest(@NotBlank @Size(max = 64) String name, @Size(max = 100) String slug) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String content) {
    }

    public record CommentStatusRequest(@NotNull CommentStatus status) {
    }

    public record GuestPasswordRequest(@NotBlank @Size(min = 6, max = 64) String newPassword) {
    }

    public record GuestStatusRequest(@NotNull UserStatus status) {
    }

    public record AdminPasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 64) String newPassword
    ) {
    }

    public record LinkItem(
            @NotBlank String label,
            @NotBlank String url,
            String type,
            Integer sortOrder
    ) {
    }

    public record CoverRequest(
            @NotBlank String title,
            String subtitle,
            String backgroundImageUrl,
            String avatarImageUrl,
            List<@Valid LinkItem> links
    ) {
    }

    public record ProfileRequest(
            @NotBlank String displayName,
            String bio,
            String avatarImageUrl,
            String email,
            String location,
            List<@Valid LinkItem> socialLinks,
            String contentMarkdown
    ) {
    }

    public record SystemConfigRequest(
            String configValue,
            @NotNull ValueType valueType,
            String description
    ) {
    }
}
