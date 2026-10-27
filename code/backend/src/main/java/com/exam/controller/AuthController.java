package com.exam.controller;

import com.exam.common.result.ApiResponse;
import com.exam.dto.LoginRequest;
import com.exam.dto.LoginResponse;
import com.exam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> me() {
        return ApiResponse.success(authService.getCurrentUserInfo());
    }
}
