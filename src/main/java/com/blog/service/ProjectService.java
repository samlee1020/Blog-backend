package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResponse;
import com.blog.domain.Project;
import com.blog.domain.ProjectStatus;
import com.blog.dto.Requests;
import com.blog.mapper.ProjectMapper;
import com.blog.security.AuthContext;
import com.blog.util.SlugUtil;
import com.blog.vo.Views;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectMapper projectMapper, ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<Views.ProjectView> publicList(int page, int size, String keyword) {
        LambdaQueryWrapper<Project> wrapper = baseQuery()
                .eq(Project::getStatus, ProjectStatus.PUBLISHED)
                .and(StringUtils.hasText(keyword), w -> w.like(Project::getTitle, keyword).or().like(Project::getDescription, keyword))
                .orderByAsc(Project::getSortOrder)
                .orderByDesc(Project::getId);
        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toView).toList(), page, size, result.getTotal());
    }

    public Views.ProjectView publicDetail(String slug) {
        Project project = projectMapper.selectOne(baseQuery()
                .eq(Project::getStatus, ProjectStatus.PUBLISHED)
                .eq(Project::getSlug, slug)
                .last("limit 1"));
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "project not found");
        }
        return toView(project);
    }

    public PageResponse<Views.ProjectView> adminList(int page, int size, ProjectStatus status, String keyword) {
        LambdaQueryWrapper<Project> wrapper = baseQuery()
                .eq(status != null, Project::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w.like(Project::getTitle, keyword).or().like(Project::getDescription, keyword))
                .orderByAsc(Project::getSortOrder)
                .orderByDesc(Project::getUpdatedAt);
        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResponse.of(result.getRecords().stream().map(this::toView).toList(), page, size, result.getTotal());
    }

    public Views.ProjectView adminDetail(Long id) {
        return toView(getExisting(id));
    }

    @Transactional
    public Views.ProjectView create(Requests.ProjectRequest request) {
        String slug = resolveSlug(request.slug(), request.title());
        ensureSlugAvailable(slug, null);
        Project project = new Project();
        project.setTitle(request.title());
        project.setSlug(slug);
        project.setDescription(request.description());
        project.setContentMarkdown(request.contentMarkdown());
        project.setImageUrl(request.imageUrl());
        project.setProjectUrl(request.projectUrl());
        project.setTagsJson(writeTags(normalizeTags(request.tags())));
        project.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        project.setStatus(request.status() == null ? ProjectStatus.DRAFT : request.status());
        project.setCreatedBy(AuthContext.currentUserId());
        project.setUpdatedBy(AuthContext.currentUserId());
        projectMapper.insert(project);
        return toView(project);
    }

    @Transactional
    public Views.ProjectView update(Long id, Requests.ProjectRequest request) {
        Project project = getExisting(id);
        String slug = resolveSlug(request.slug(), request.title());
        ensureSlugAvailable(slug, id);
        project.setTitle(request.title());
        project.setSlug(slug);
        project.setDescription(request.description());
        project.setContentMarkdown(request.contentMarkdown());
        project.setImageUrl(request.imageUrl());
        project.setProjectUrl(request.projectUrl());
        project.setTagsJson(writeTags(normalizeTags(request.tags())));
        project.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        project.setStatus(request.status() == null ? ProjectStatus.DRAFT : request.status());
        project.setUpdatedBy(AuthContext.currentUserId());
        projectMapper.updateById(project);
        return toView(project);
    }

    @Transactional
    public boolean delete(Long id) {
        Project project = getExisting(id);
        project.setDeletedAt(LocalDateTime.now());
        project.setUpdatedBy(AuthContext.currentUserId());
        projectMapper.updateById(project);
        return true;
    }

    public Project getExisting(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "project not found");
        }
        return project;
    }

    public Views.ProjectView toView(Project project) {
        String detailUrl = "/projects/" + project.getSlug();
        return new Views.ProjectView(project.getId(), project.getTitle(), project.getSlug(), detailUrl, project.getDescription(),
                project.getContentMarkdown(), project.getImageUrl(), project.getProjectUrl(), readTags(project.getTagsJson()),
                project.getSortOrder(), project.getStatus(), project.getCreatedAt(), project.getUpdatedAt());
    }

    private void ensureSlugAvailable(String slug, Long excludedId) {
        LambdaQueryWrapper<Project> wrapper = baseQuery().eq(Project::getSlug, slug);
        if (excludedId != null) {
            wrapper.ne(Project::getId, excludedId);
        }
        if (projectMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "project slug already exists");
        }
    }

    private String resolveSlug(String requestedSlug, String title) {
        String slug = StringUtils.hasText(requestedSlug) ? SlugUtil.from(requestedSlug) : SlugUtil.from(title);
        if (!StringUtils.hasText(slug)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "project slug is required");
        }
        return slug;
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "invalid project tags");
        }
    }

    private List<String> readTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private LambdaQueryWrapper<Project> baseQuery() {
        return new LambdaQueryWrapper<Project>().isNull(Project::getDeletedAt);
    }
}
