package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.domain.ArticleTag;
import com.blog.domain.Tag;
import com.blog.dto.Requests;
import com.blog.mapper.ArticleTagMapper;
import com.blog.mapper.TagMapper;
import com.blog.util.SlugUtil;
import com.blog.vo.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class TagService {
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    public TagService(TagMapper tagMapper, ArticleTagMapper articleTagMapper) {
        this.tagMapper = tagMapper;
        this.articleTagMapper = articleTagMapper;
    }

    public List<Views.TagView> publicList() {
        return tagMapper.selectList(baseQuery().orderByAsc(Tag::getName)).stream().map(this::toView).toList();
    }

    public List<Tag> findByArticleId(Long articleId) {
        List<Long> tagIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId))
                .stream().map(ArticleTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagMapper.selectList(baseQuery().in(Tag::getId, tagIds));
    }

    public List<Views.TagView> viewsByArticleId(Long articleId) {
        return findByArticleId(articleId).stream().map(this::toView).toList();
    }

    public void ensureAllExist(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Long count = tagMapper.selectCount(baseQuery().in(Tag::getId, tagIds));
        if (count != tagIds.stream().distinct().count()) {
            throw new BizException(ErrorCode.NOT_FOUND, "tag not found");
        }
    }

    @Transactional
    public Views.TagView create(Requests.TagRequest request) {
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.name());
        ensureNameAndSlugAvailable(request.name(), slug, null);
        Tag tag = new Tag();
        tag.setName(request.name());
        tag.setSlug(slug);
        tagMapper.insert(tag);
        return toView(tag);
    }

    @Transactional
    public boolean update(Long id, Requests.TagRequest request) {
        Tag tag = getExisting(id);
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.name());
        ensureNameAndSlugAvailable(request.name(), slug, id);
        tag.setName(request.name());
        tag.setSlug(slug);
        tagMapper.updateById(tag);
        return true;
    }

    @Transactional
    public boolean delete(Long id) {
        Tag tag = getExisting(id);
        tag.setDeletedAt(LocalDateTime.now());
        tagMapper.updateById(tag);
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        return true;
    }

    public Tag getExisting(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || tag.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "tag not found");
        }
        return tag;
    }

    public Views.TagView toView(Tag tag) {
        return new Views.TagView(tag.getId(), tag.getName(), tag.getSlug());
    }

    private void ensureNameAndSlugAvailable(String name, String slug, Long excludedId) {
        LambdaQueryWrapper<Tag> wrapper = baseQuery().and(w -> w.eq(Tag::getName, name).or().eq(Tag::getSlug, slug));
        if (excludedId != null) {
            wrapper.ne(Tag::getId, excludedId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "tag name or slug already exists");
        }
    }

    private LambdaQueryWrapper<Tag> baseQuery() {
        return new LambdaQueryWrapper<Tag>().isNull(Tag::getDeletedAt);
    }
}
