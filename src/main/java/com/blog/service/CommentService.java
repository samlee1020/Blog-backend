package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResponse;
import com.blog.domain.*;
import com.blog.dto.Requests;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.mapper.SystemConfigMapper;
import com.blog.mapper.UserMapper;
import com.blog.security.LoginUser;
import com.blog.util.ContentSanitizer;
import com.blog.vo.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final SystemConfigMapper systemConfigMapper;

    public CommentService(CommentMapper commentMapper, UserMapper userMapper, ArticleMapper articleMapper,
                          ArticleService articleService, SystemConfigMapper systemConfigMapper) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.articleMapper = articleMapper;
        this.articleService = articleService;
        this.systemConfigMapper = systemConfigMapper;
    }

    public PageResponse<Views.CommentView> publicComments(String slug, int page, int size) {
        Article article = articleService.findPublishedBySlug(slug);
        Page<Comment> result = commentMapper.selectPage(new Page<>(page, size), baseQuery()
                .eq(Comment::getArticleId, article.getId())
                .eq(Comment::getStatus, CommentStatus.VISIBLE)
                .orderByDesc(Comment::getCreatedAt));
        return PageResponse.of(result.getRecords().stream().map(this::toCommentView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public Views.CommentView create(String slug, Requests.CommentRequest request, LoginUser loginUser, HttpServletRequest servletRequest) {
        Article article = articleService.findPublishedBySlug(slug);
        Comment comment = new Comment();
        comment.setArticleId(article.getId());
        comment.setUserId(loginUser.userId());
        comment.setContent(ContentSanitizer.cleanComment(request.content()));
        comment.setStatus(defaultStatus());
        comment.setIpAddress(clientIp(servletRequest));
        String ua = servletRequest.getHeader("User-Agent");
        comment.setUserAgent(ua == null || ua.length() <= 500 ? ua : ua.substring(0, 500));
        commentMapper.insert(comment);
        return toCommentView(comment);
    }

    public PageResponse<Views.AdminCommentView> adminList(int page, int size, CommentStatus status, Long articleId, String username) {
        LambdaQueryWrapper<Comment> wrapper = baseQuery()
                .eq(status != null, Comment::getStatus, status)
                .eq(articleId != null, Comment::getArticleId, articleId)
                .orderByDesc(Comment::getCreatedAt);
        if (StringUtils.hasText(username)) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getUsername, username).isNull(User::getDeletedAt))
                    .stream().map(User::getId).toList();
            if (userIds.isEmpty()) {
                wrapper.eq(Comment::getUserId, -1L);
            } else {
                wrapper.in(Comment::getUserId, userIds);
            }
        }
        Page<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toAdminView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public boolean updateStatus(Long id, Requests.CommentStatusRequest request) {
        Comment comment = getExisting(id);
        comment.setStatus(request.status());
        commentMapper.updateById(comment);
        return true;
    }

    @Transactional
    public boolean delete(Long id) {
        Comment comment = getExisting(id);
        comment.setDeletedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        return true;
    }

    private Comment getExisting(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "comment not found");
        }
        return comment;
    }

    private Views.CommentView toCommentView(Comment comment) {
        User user = userMapper.selectById(comment.getUserId());
        Views.AuthorView author = new Views.AuthorView(user.getId(), user.getUsername(), user.getNickname());
        return new Views.CommentView(comment.getId(), comment.getContent(), comment.getStatus(), author, comment.getCreatedAt());
    }

    private Views.AdminCommentView toAdminView(Comment comment) {
        Article article = articleMapper.selectById(comment.getArticleId());
        Views.CommentView view = toCommentView(comment);
        return new Views.AdminCommentView(comment.getId(), comment.getArticleId(), article == null ? null : article.getTitle(),
                comment.getContent(), comment.getStatus(), view.author(), comment.getIpAddress(), comment.getUserAgent(), comment.getCreatedAt());
    }

    private CommentStatus defaultStatus() {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "comment.defaultStatus").last("limit 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return CommentStatus.VISIBLE;
        }
        return CommentStatus.valueOf(config.getConfigValue());
    }

    private LambdaQueryWrapper<Comment> baseQuery() {
        return new LambdaQueryWrapper<Comment>().isNull(Comment::getDeletedAt);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
