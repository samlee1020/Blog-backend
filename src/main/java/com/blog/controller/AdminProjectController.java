package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.domain.ProjectStatus;
import com.blog.dto.Requests;
import com.blog.service.ProjectService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectController {
    private final ProjectService projectService;

    public AdminProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Views.ProjectView>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                              @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                                              @RequestParam(required = false) ProjectStatus status,
                                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.success(projectService.adminList(page, size, status, keyword));
    }

    @PostMapping
    public ApiResponse<Views.ProjectView> create(@Valid @RequestBody Requests.ProjectRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.ProjectView> detail(@PathVariable Long id) {
        return ApiResponse.success(projectService.adminDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Views.ProjectView> update(@PathVariable Long id, @Valid @RequestBody Requests.ProjectRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(projectService.delete(id));
    }
}
