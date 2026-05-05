package com.blog.controller;

import com.blog.common.ApiResponse;
import com.blog.dto.Requests;
import com.blog.security.AuthContext;
import com.blog.service.AuthService;
import com.blog.vo.Views;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/guest/register")
    public ApiResponse<Views.UserView> registerGuest(@Valid @RequestBody Requests.GuestRegisterRequest request) {
        return ApiResponse.success(authService.registerGuest(request));
    }

    @PostMapping("/login")
    public ApiResponse<Views.LoginView> login(@Valid @RequestBody Requests.LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, servletRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.success(authService.logout(AuthContext.currentUser()));
    }

    @GetMapping("/me")
    public ApiResponse<Views.UserView> me() {
        return ApiResponse.success(new Views.UserView(AuthContext.currentUser().userId(), AuthContext.currentUser().username(),
                AuthContext.currentUser().nickname(), AuthContext.currentUser().role()));
    }
}
