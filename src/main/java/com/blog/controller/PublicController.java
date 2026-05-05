package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.dto.Requests;
import com.blog.security.AuthContext;
import com.blog.service.*;
import com.blog.vo.Views;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class PublicController {
    private final SiteService siteService;
    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;
    private final ProjectService projectService;

    public PublicController(SiteService siteService, ArticleService articleService, CategoryService categoryService,
                            TagService tagService, CommentService commentService, ProjectService projectService) {
        this.siteService = siteService;
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.commentService = commentService;
        this.projectService = projectService;
    }

    @GetMapping("/cover")
    public ApiResponse<Views.CoverView> cover() {
        return ApiResponse.success(siteService.cover());
    }

    @GetMapping("/profile")
    public ApiResponse<Views.ProfileView> profile() {
        return ApiResponse.success(siteService.profile());
    }

    @GetMapping("/articles")
    public ApiResponse<PageResponse<Views.ArticleSummaryView>> articles(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String tagSlug,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(articleService.publicList(page, size, categorySlug, tagSlug, keyword));
    }

    @GetMapping("/articles/{slug}")
    public ApiResponse<Views.ArticleDetailView> articleDetail(@PathVariable String slug) {
        return ApiResponse.success(articleService.publicDetail(slug));
    }

    @GetMapping("/projects")
    public ApiResponse<PageResponse<Views.ProjectView>> projects(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                 @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                                                 @RequestParam(required = false) String keyword) {
        return ApiResponse.success(projectService.publicList(page, size, keyword));
    }

    @GetMapping("/projects/{slug}")
    public ApiResponse<Views.ProjectView> projectDetail(@PathVariable String slug) {
        return ApiResponse.success(projectService.publicDetail(slug));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Views.CategoryView>> categories() {
        return ApiResponse.success(categoryService.publicList());
    }

    @GetMapping("/tags")
    public ApiResponse<List<Views.TagView>> tags() {
        return ApiResponse.success(tagService.publicList());
    }

    @GetMapping("/articles/{slug}/comments")
    public ApiResponse<PageResponse<Views.CommentView>> comments(@PathVariable String slug,
                                                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(commentService.publicComments(slug, page, size));
    }

    @PostMapping("/articles/{slug}/comments")
    public ApiResponse<Views.CommentView> createComment(@PathVariable String slug,
                                                        @Valid @RequestBody Requests.CommentRequest request,
                                                        HttpServletRequest servletRequest) {
        return ApiResponse.success(commentService.create(slug, request, AuthContext.currentUser(), servletRequest));
    }
}
