package com.ashutosh.banking.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CreateAccountRequest(@NotBlank @Size(min=8,max=24) String accountNumber, @NotBlank @Size(max=120) String holderName, @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal openingBalance) {}
