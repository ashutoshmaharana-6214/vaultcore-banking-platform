package com.ashutosh.banking.controller;

import com.ashutosh.banking.dto.PhoneVerificationConfirmRequest;
import com.ashutosh.banking.dto.PhoneVerificationStartRequest;
import com.ashutosh.banking.dto.SmsPreferenceRequest;
import com.ashutosh.banking.service.PhoneVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class PhoneController {
    private final PhoneVerificationService service;

    public PhoneController(PhoneVerificationService service) {
        this.service = service;
    }

    @PostMapping("/phone/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void start(@AuthenticationPrincipal Jwt jwt,
                      @Valid @RequestBody PhoneVerificationStartRequest request) {
        service.startVerification(jwt.getSubject(), request.phoneNumber());
    }

    @PostMapping("/phone/verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody PhoneVerificationConfirmRequest request) {
        service.confirmVerification(jwt.getSubject(), request.phoneNumber(), request.code());
    }

    @PutMapping("/notifications/sms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePreference(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody SmsPreferenceRequest request) {
        service.setSmsAlertsEnabled(jwt.getSubject(), request.enabled());
    }
}
