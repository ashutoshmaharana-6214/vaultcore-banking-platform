package com.ashutosh.banking.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record TransferResponse(String reference, String fromAccount, String toAccount, BigDecimal amount, String status, Instant createdAt) {}
