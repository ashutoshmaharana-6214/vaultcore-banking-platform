package com.ashutosh.banking.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record AccountResponse(Long id, String accountNumber, String holderName, BigDecimal balance, long version, Instant createdAt) {}
