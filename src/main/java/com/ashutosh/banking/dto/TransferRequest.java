package com.ashutosh.banking.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record TransferRequest(@NotBlank String fromAccount, @NotBlank String toAccount, @NotNull @DecimalMin(value="0.01") @Digits(integer=17,fraction=2) BigDecimal amount) {}
