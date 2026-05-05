package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.common.PageResponse;
import com.blog.dto.Requests;
import com.blog.service.UserService;
import com.blog.vo.Views;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/guests")
    public ApiResponse<PageResponse<Views.GuestView>> guests(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                             @RequestParam(required = false) String username) {
        return ApiResponse.success(userService.guests(page, size, username));
    }

    @PatchMapping("/guests/{id}/password")
    public ApiResponse<Boolean> resetPassword(@PathVariable Long id, @Valid @RequestBody Requests.GuestPasswordRequest request) {
        return ApiResponse.success(userService.resetGuestPassword(id, request));
    }

    @PatchMapping("/guests/{id}/status")
    public ApiResponse<Boolean> updateStatus(@PathVariable Long id, @Valid @RequestBody Requests.GuestStatusRequest request) {
        return ApiResponse.success(userService.updateGuestStatus(id, request));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody Requests.AdminPasswordRequest request) {
        return ApiResponse.success(userService.changeAdminPassword(request));
    }
}
