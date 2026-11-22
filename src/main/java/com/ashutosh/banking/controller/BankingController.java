package com.ashutosh.banking.controller;

import com.ashutosh.banking.dto.*;
import com.ashutosh.banking.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BankingController {
    private final BankingService service;

    public BankingController(BankingService service) {
        this.service = service;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.createAccount(request, jwt.getSubject());
    }

    @GetMapping("/accounts/{accountNumber}")
    public AccountResponse get(@PathVariable String accountNumber, @AuthenticationPrincipal Jwt jwt) {
        return service.getAccount(accountNumber, jwt.getSubject());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.transfer(request, idempotencyKey, jwt.getSubject());
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public List<TransferResponse> history(@PathVariable String accountNumber, @AuthenticationPrincipal Jwt jwt) {
        return service.history(accountNumber, jwt.getSubject());
    }
}
