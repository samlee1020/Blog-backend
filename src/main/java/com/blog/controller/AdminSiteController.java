package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.dto.Requests;
import com.blog.service.SiteService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminSiteController {
    private final SiteService siteService;

    public AdminSiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/cover")
    public ApiResponse<Views.CoverView> cover() {
        return ApiResponse.success(siteService.cover());
    }

    @PutMapping("/cover")
    public ApiResponse<Views.CoverView> updateCover(@Valid @RequestBody Requests.CoverRequest request) {
        return ApiResponse.success(siteService.updateCover(request));
    }

    @GetMapping("/profile")
    public ApiResponse<Views.ProfileView> profile() {
        return ApiResponse.success(siteService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<Views.ProfileView> updateProfile(@Valid @RequestBody Requests.ProfileRequest request) {
        return ApiResponse.success(siteService.updateProfile(request));
    }

    @GetMapping("/system-configs")
    public ApiResponse<List<Views.SystemConfigView>> configs() {
        return ApiResponse.success(siteService.systemConfigs());
    }

    @PutMapping("/system-configs/{configKey}")
    public ApiResponse<Views.SystemConfigView> updateConfig(@PathVariable String configKey,
                                                            @Valid @RequestBody Requests.SystemConfigRequest request) {
        return ApiResponse.success(siteService.updateSystemConfig(configKey, request));
    }
}
