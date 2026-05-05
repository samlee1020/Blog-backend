package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.domain.Article;
import com.blog.domain.Category;
import com.blog.dto.Requests;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.util.SlugUtil;
import com.blog.vo.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public CategoryService(CategoryMapper categoryMapper, ArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    public List<Views.CategoryView> publicList() {
        return categoryMapper.selectList(baseQuery().orderByAsc(Category::getSortOrder).orderByAsc(Category::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public Views.CategoryView create(Requests.CategoryRequest request) {
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.name());
        ensureSlugAvailable(slug, null);
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        categoryMapper.insert(category);
        return toView(category);
    }

    @Transactional
    public boolean update(Long id, Requests.CategoryRequest request) {
        Category category = getExisting(id);
        String slug = StringUtils.hasText(request.slug()) ? SlugUtil.from(request.slug()) : SlugUtil.from(request.name());
        ensureSlugAvailable(slug, id);
        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        categoryMapper.updateById(category);
        return true;
    }

    @Transactional
    public boolean delete(Long id) {
        Category category = getExisting(id);
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getCategoryId, id)
                .isNull(Article::getDeletedAt));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "category is used by articles");
        }
        category.setDeletedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
        return true;
    }

    public Category getExisting(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null || category.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "category not found");
        }
        return category;
    }

    public Views.CategoryView toView(Category category) {
        if (category == null) {
            return null;
        }
        return new Views.CategoryView(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.getSortOrder());
    }

    private void ensureSlugAvailable(String slug, Long excludedId) {
        LambdaQueryWrapper<Category> wrapper = baseQuery().eq(Category::getSlug, slug);
        if (excludedId != null) {
            wrapper.ne(Category::getId, excludedId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "category slug already exists");
        }
    }

    private LambdaQueryWrapper<Category> baseQuery() {
        return new LambdaQueryWrapper<Category>().isNull(Category::getDeletedAt);
    }
}
