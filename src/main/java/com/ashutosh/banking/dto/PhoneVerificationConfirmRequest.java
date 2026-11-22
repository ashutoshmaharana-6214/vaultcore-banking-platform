package com.ashutosh.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerificationConfirmRequest(
    @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
    String phoneNumber,
    @NotBlank @Pattern(regexp = "^[0-9]{4,10}$")
    String code
) {}
