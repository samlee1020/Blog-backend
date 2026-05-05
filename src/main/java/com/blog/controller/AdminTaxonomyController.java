package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.dto.Requests;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminTaxonomyController {
    private final CategoryService categoryService;
    private final TagService tagService;

    public AdminTaxonomyController(CategoryService categoryService, TagService tagService) {
        this.categoryService = categoryService;
        this.tagService = tagService;
    }

    @PostMapping("/categories")
    public ApiResponse<Views.CategoryView> createCategory(@Valid @RequestBody Requests.CategoryRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Boolean> updateCategory(@PathVariable Long id, @Valid @RequestBody Requests.CategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Boolean> deleteCategory(@PathVariable Long id) {
        return ApiResponse.success(categoryService.delete(id));
    }

    @PostMapping("/tags")
    public ApiResponse<Views.TagView> createTag(@Valid @RequestBody Requests.TagRequest request) {
        return ApiResponse.success(tagService.create(request));
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Boolean> updateTag(@PathVariable Long id, @Valid @RequestBody Requests.TagRequest request) {
        return ApiResponse.success(tagService.update(id, request));
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Boolean> deleteTag(@PathVariable Long id) {
        return ApiResponse.success(tagService.delete(id));
    }
}
