package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.domain.ArticleStatus;
import com.blog.dto.Requests;
import com.blog.service.ArticleService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {
    private final ArticleService articleService;

    public AdminArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Views.ArticleSummaryView>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                    @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                                                    @RequestParam(required = false) ArticleStatus status,
                                                                    @RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(articleService.adminList(page, size, status, keyword, categoryId));
    }

    @PostMapping
    public ApiResponse<Views.ArticleMutationView> create(@Valid @RequestBody Requests.ArticleRequest request) {
        return ApiResponse.success(articleService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.ArticleDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(articleService.adminDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Views.ArticleMutationView> update(@PathVariable Long id, @Valid @RequestBody Requests.ArticleRequest request) {
        return ApiResponse.success(articleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(articleService.delete(id));
    }
}
