package com.ashutosh.banking.controller;
import com.ashutosh.banking.dto.*;
import com.ashutosh.banking.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api")
public class BankingController {
 private final BankingService service; public BankingController(BankingService service){this.service=service;}
 @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED) public AccountResponse create(@Valid @RequestBody CreateAccountRequest request){return service.createAccount(request);}
 @GetMapping("/accounts/{accountNumber}") public AccountResponse get(@PathVariable String accountNumber){return service.getAccount(accountNumber);}
@PostMapping("/transfers")
@ResponseStatus(HttpStatus.CREATED)
public TransferResponse transfer(
        @Valid @RequestBody TransferRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

    return service.transfer(request, idempotencyKey);
}
 @GetMapping("/accounts/{accountNumber}/transactions") public List<TransferResponse> history(@PathVariable String accountNumber){return service.history(accountNumber);}
}
