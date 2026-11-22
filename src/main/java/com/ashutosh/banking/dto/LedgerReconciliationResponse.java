package com.ashutosh.banking.dto;

import java.math.BigDecimal;

public record LedgerReconciliationResponse(
        String transactionReference,
        int postingCount,
        BigDecimal netAmount,
        boolean balanced
) {}
