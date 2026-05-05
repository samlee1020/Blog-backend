package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.domain.MediaUsageType;
import com.blog.service.MediaService;
import com.blog.vo.Views;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin/media/images")
public class AdminMediaController {
    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public ApiResponse<Views.MediaAssetView> upload(@RequestPart("file") MultipartFile file,
                                                    @RequestParam MediaUsageType usageType) {
        return ApiResponse.success(mediaService.upload(file, usageType));
    }

    @GetMapping
    public ApiResponse<PageResponse<Views.MediaAssetView>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                                @RequestParam(required = false) MediaUsageType usageType) {
        return ApiResponse.success(mediaService.list(page, size, usageType));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(mediaService.delete(id));
    }
}
