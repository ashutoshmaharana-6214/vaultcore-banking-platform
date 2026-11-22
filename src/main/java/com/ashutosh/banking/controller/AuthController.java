package com.ashutosh.banking.controller;

import com.ashutosh.banking.dto.AccessTokenResponse;
import com.ashutosh.banking.dto.LoginRequest;
import com.ashutosh.banking.dto.RegisterRequest;
import com.ashutosh.banking.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public AccessTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
