package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.domain.CommentStatus;
import com.blog.dto.Requests;
import com.blog.service.CommentService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {
    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Views.AdminCommentView>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                                  @RequestParam(required = false) CommentStatus status,
                                                                  @RequestParam(required = false) Long articleId,
                                                                  @RequestParam(required = false) String username) {
        return ApiResponse.success(commentService.adminList(page, size, status, articleId, username));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Boolean> updateStatus(@PathVariable Long id, @Valid @RequestBody Requests.CommentStatusRequest request) {
        return ApiResponse.success(commentService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(commentService.delete(id));
    }
}
