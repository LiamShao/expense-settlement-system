package com.example.expense.controller;

import com.example.expense.common.ApiResponse;
import com.example.expense.dto.request.LoginRequest;
import com.example.expense.dto.response.AuthResponse;
import com.example.expense.dto.response.UserResponse;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "ログインに成功しました。");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal SecurityUser securityUser) {
        return ApiResponse.success(authService.getCurrentUser(securityUser));
    }
}
