package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.PageResponse;
import com.blog.common.ErrorCode;
import com.blog.domain.*;
import com.blog.dto.Requests;
import com.blog.mapper.*;
import com.blog.security.AuthContext;
import com.blog.util.SlugUtil;
import com.blog.vo.Views;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ArticleService {
    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final ArticleViewCounterService articleViewCounterService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ArticleService(ArticleMapper articleMapper, CategoryMapper categoryMapper, TagMapper tagMapper,
                          ArticleTagMapper articleTagMapper, CategoryService categoryService, TagService tagService,
                          ArticleViewCounterService articleViewCounterService, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.articleViewCounterService = articleViewCounterService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PageResponse<Views.ArticleSummaryView> publicList(int page, int size, String categorySlug, String tagSlug, String keyword) {
        String cacheKey = "article:list:" + page + ":" + size + ":" + DigestUtils.md5DigestAsHex(
                ((categorySlug == null ? "" : categorySlug) + "|" + (tagSlug == null ? "" : tagSlug) + "|" + (keyword == null ? "" : keyword))
                        .getBytes(StandardCharsets.UTF_8));
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete(cacheKey);
            }
        }
        LambdaQueryWrapper<Article> wrapper = publicArticleQuery(categorySlug, tagSlug, keyword)
                .orderByDesc(Article::getPublishedAt).orderByDesc(Article::getId);
        Page<Article> result = articleMapper.selectPage(new Page<>(page, size), wrapper);
        PageResponse<Views.ArticleSummaryView> response = PageResponse.of(result.getRecords().stream().map(this::toSummaryView).toList(),
                page, size, result.getTotal());
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofMinutes(5));
        } catch (JsonProcessingException ignored) {
        }
        return response;
    }

    public Views.ArticleDetailView publicDetail(String slug) {
        String cacheKey = "article:detail:" + slug;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                Views.ArticleDetailView view = objectMapper.readValue(cached, Views.ArticleDetailView.class);
                articleViewCounterService.increment(view.id());
                return view;
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete(cacheKey);
            }
        }
        Article article = articleMapper.selectOne(baseArticleQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(Article::getSlug, slug)
                .last("limit 1"));
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "article not found");
        }
        Views.ArticleDetailView view = toDetailView(article);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(view), Duration.ofMinutes(10));
        } catch (JsonProcessingException ignored) {
        }
        articleViewCounterService.increment(article.getId());
        return view;
    }

    public PageResponse<Views.ArticleSummaryView> adminList(int page, int size, ArticleStatus status, String keyword, Long categoryId) {
        LambdaQueryWrapper<Article> wrapper = baseArticleQuery()
                .eq(status != null, Article::getStatus, status)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                .and(StringUtils.hasText(keyword), w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword))
                .orderByDesc(Article::getUpdatedAt);
        Page<Article> result = articleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toSummaryView).toList(), page, size, result.getTotal());
    }

    public Views.ArticleDetailView adminDetail(Long id) {
        return toDetailView(getExisting(id));
    }

    @Transactional
    public Views.ArticleMutationView create(Requests.ArticleRequest request) {
        if (request.categoryId() != null) {
            categoryService.getExisting(request.categoryId());
        }
        tagService.ensureAllExist(request.tagIds());
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.title());
        ensureSlugAvailable(slug, null);
        ArticleStatus status = request.status() == null ? ArticleStatus.DRAFT : request.status();
        Article article = new Article();
        article.setTitle(request.title());
        article.setSlug(slug);
        article.setSummary(request.summary());
        article.setCoverImageUrl(request.coverImageUrl());
        article.setContentMarkdown(request.contentMarkdown());
        article.setContentHtml(null);
        article.setCategoryId(request.categoryId());
        article.setStatus(status);
        article.setViewCount(0L);
        article.setPublishedAt(status == ArticleStatus.PUBLISHED ? LocalDateTime.now() : null);
        article.setCreatedBy(AuthContext.currentUserId());
        article.setUpdatedBy(AuthContext.currentUserId());
        articleMapper.insert(article);
        replaceTags(article.getId(), request.tagIds());
        evictArticleCaches(article.getSlug());
        return toMutationView(article);
    }

    @Transactional
    public Views.ArticleMutationView update(Long id, Requests.ArticleRequest request) {
        Article article = getExisting(id);
        if (request.categoryId() != null) {
            categoryService.getExisting(request.categoryId());
        }
        tagService.ensureAllExist(request.tagIds());
        String oldSlug = article.getSlug();
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.title());
        ensureSlugAvailable(slug, id);
        ArticleStatus newStatus = request.status() == null ? ArticleStatus.DRAFT : request.status();
        if (article.getStatus() != ArticleStatus.PUBLISHED && newStatus == ArticleStatus.PUBLISHED && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        article.setTitle(request.title());
        article.setSlug(slug);
        article.setSummary(request.summary());
        article.setCoverImageUrl(request.coverImageUrl());
        article.setContentMarkdown(request.contentMarkdown());
        article.setCategoryId(request.categoryId());
        article.setStatus(newStatus);
        article.setUpdatedBy(AuthContext.currentUserId());
        articleMapper.updateById(article);
        replaceTags(article.getId(), request.tagIds());
        evictArticleCaches(oldSlug);
        evictArticleCaches(article.getSlug());
        return toMutationView(article);
    }

    @Transactional
    public boolean delete(Long id) {
        Article article = getExisting(id);
        article.setDeletedAt(LocalDateTime.now());
        article.setUpdatedBy(AuthContext.currentUserId());
        articleMapper.updateById(article);
        evictArticleCaches(article.getSlug());
        return true;
    }

    public Article findPublishedBySlug(String slug) {
        Article article = articleMapper.selectOne(baseArticleQuery()
                .eq(Article::getSlug, slug)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .last("limit 1"));
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "article not found");
        }
        return article;
    }

    public Article getExisting(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "article not found");
        }
        return article;
    }

    public Views.ArticleSummaryView toSummaryView(Article article) {
        return new Views.ArticleSummaryView(article.getId(), article.getTitle(), article.getSlug(), article.getSummary(),
                article.getCoverImageUrl(), categoryView(article.getCategoryId()), tagService.viewsByArticleId(article.getId()),
                article.getStatus(), article.getViewCount(), article.getPublishedAt(), article.getCreatedAt(), article.getUpdatedAt());
    }

    public Views.ArticleDetailView toDetailView(Article article) {
        return new Views.ArticleDetailView(article.getId(), article.getTitle(), article.getSlug(), article.getSummary(),
                article.getCoverImageUrl(), article.getContentMarkdown(), article.getContentHtml(), categoryView(article.getCategoryId()),
                tagService.viewsByArticleId(article.getId()), article.getStatus(), article.getViewCount(), article.getPublishedAt(),
                article.getCreatedAt(), article.getUpdatedAt());
    }

    private Views.ArticleMutationView toMutationView(Article article) {
        return new Views.ArticleMutationView(article.getId(), article.getTitle(), article.getSlug(), article.getStatus(),
                article.getCreatedAt(), article.getUpdatedAt());
    }

    private Views.CategoryView categoryView(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null || category.getDeletedAt() != null ? null : categoryService.toView(category);
    }

    private void replaceTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (CollectionUtils.isEmpty(tagIds)) {
            return;
        }
        tagIds.stream().distinct().forEach(tagId -> {
            ArticleTag relation = new ArticleTag();
            relation.setArticleId(articleId);
            relation.setTagId(tagId);
            articleTagMapper.insert(relation);
        });
    }

    private void ensureSlugAvailable(String slug, Long excludedId) {
        LambdaQueryWrapper<Article> wrapper = baseArticleQuery().eq(Article::getSlug, slug);
        if (excludedId != null) {
            wrapper.ne(Article::getId, excludedId);
        }
        if (articleMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "article slug already exists");
        }
    }

    private LambdaQueryWrapper<Article> publicArticleQuery(String categorySlug, String tagSlug, String keyword) {
        LambdaQueryWrapper<Article> wrapper = baseArticleQuery().eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .and(StringUtils.hasText(keyword), w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
        if (StringUtils.hasText(categorySlug)) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getSlug, categorySlug).isNull(Category::getDeletedAt).last("limit 1"));
            wrapper.eq(Article::getCategoryId, category == null ? -1L : category.getId());
        }
        if (StringUtils.hasText(tagSlug)) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getSlug, tagSlug).isNull(Tag::getDeletedAt).last("limit 1"));
            List<Long> articleIds = tag == null ? Collections.emptyList() : articleTagMapper
                    .selectList(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tag.getId()))
                    .stream().map(ArticleTag::getArticleId).toList();
            wrapper.in(!articleIds.isEmpty(), Article::getId, articleIds);
            if (articleIds.isEmpty()) {
                wrapper.eq(Article::getId, -1L);
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Article> baseArticleQuery() {
        return new LambdaQueryWrapper<Article>().isNull(Article::getDeletedAt);
    }

    private void evictArticleCaches(String slug) {
        if (StringUtils.hasText(slug)) {
            redisTemplate.delete("article:detail:" + slug);
        }
        SetUtils.deleteByPattern(redisTemplate, "article:list:*");
    }
}
