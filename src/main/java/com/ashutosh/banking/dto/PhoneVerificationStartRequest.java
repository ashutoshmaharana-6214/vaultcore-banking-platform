package com.ashutosh.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerificationStartRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Use E.164 format, e.g. +919876543210")
    String phoneNumber
) {}
