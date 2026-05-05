package com.blog.vo;

import com.blog.domain.ArticleStatus;
import com.blog.domain.CommentStatus;
import com.blog.domain.MediaUsageType;
import com.blog.domain.ProjectStatus;
import com.blog.domain.UserRole;
import com.blog.domain.UserStatus;
import com.blog.domain.ValueType;
import com.blog.dto.Requests.LinkItem;

import java.time.LocalDateTime;
import java.util.List;

public final class Views {
    private Views() {
    }

    public record UserView(Long id, String username, String nickname, UserRole role) {
    }

    public record LoginView(String token, long expiresIn, UserView user) {
    }

    public record GuestView(Long id, String username, String nickname, UserStatus status, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record CategoryView(Long id, String name, String slug, String description, Integer sortOrder) {
    }

    public record TagView(Long id, String name, String slug) {
    }

    public record ArticleSummaryView(
            Long id,
            String title,
            String slug,
            String summary,
            String coverImageUrl,
            CategoryView category,
            List<TagView> tags,
            ArticleStatus status,
            Long viewCount,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ArticleDetailView(
            Long id,
            String title,
            String slug,
            String summary,
            String coverImageUrl,
            String contentMarkdown,
            String contentHtml,
            CategoryView category,
            List<TagView> tags,
            ArticleStatus status,
            Long viewCount,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ArticleMutationView(Long id, String title, String slug, ArticleStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ProjectView(
            Long id,
            String title,
            String slug,
            String detailUrl,
            String description,
            String contentMarkdown,
            String imageUrl,
            String projectUrl,
            List<String> tags,
            Integer sortOrder,
            ProjectStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record AuthorView(Long id, String username, String nickname) {
    }

    public record CommentView(Long id, String content, CommentStatus status, AuthorView author, LocalDateTime createdAt) {
    }

    public record AdminCommentView(
            Long id,
            Long articleId,
            String articleTitle,
            String content,
            CommentStatus status,
            AuthorView author,
            String ipAddress,
            String userAgent,
            LocalDateTime createdAt
    ) {
    }

    public record CoverView(String title, String subtitle, String backgroundImageUrl, String avatarImageUrl, List<LinkItem> links) {
    }

    public record ProfileView(
            String displayName,
            String bio,
            String avatarImageUrl,
            String email,
            String location,
            List<LinkItem> socialLinks,
            String contentMarkdown
    ) {
    }

    public record MediaAssetView(
            Long id,
            String originalFilename,
            String contentType,
            Long fileSize,
            MediaUsageType usageType,
            String url,
            LocalDateTime createdAt
    ) {
    }

    public record SystemConfigView(String configKey, String configValue, ValueType valueType, String description, LocalDateTime updatedAt) {
    }
}
