package com.ashutosh.banking.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}
