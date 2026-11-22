package com.ashutosh.banking.dto;

import jakarta.validation.constraints.NotNull;

public record SmsPreferenceRequest(@NotNull Boolean enabled) {}
