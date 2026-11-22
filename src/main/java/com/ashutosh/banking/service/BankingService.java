package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.*;
import com.ashutosh.banking.entity.*;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;

@Service
public class BankingService {
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final CacheManager cacheManager;

    public BankingService(AccountRepository accounts, BankTransactionRepository transactions,
                          CacheManager cacheManager) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, String requester) {
        if (request.openingBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Initial funding is not available through this API");
        }
        if (accounts.findByAccountNumber(request.accountNumber()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Account number already exists");
        }
        Account saved = accounts.save(
                new Account(request.accountNumber(), request.holderName(), BigDecimal.ZERO, requester));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "account-balance", key = "#accountNumber + ':' + #requester")
    public AccountResponse getAccount(String accountNumber, String requester) {
        Account account = accounts.findByAccountNumberAndOwnerEmail(accountNumber, requester)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        return toResponse(account);
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request, String idempotencyKey, String requester) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,100}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Valid Idempotency-Key is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer amount must be positive");
        }
        if (request.fromAccount().equals(request.toAccount())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source and destination must differ");
        }

        // Deterministic lock order prevents opposite-direction transfers from deadlocking.
        String first = request.fromAccount().compareTo(request.toAccount()) < 0
                ? request.fromAccount() : request.toAccount();
        String second = first.equals(request.fromAccount()) ? request.toAccount() : request.fromAccount();

        Account a = accounts.findByAccountNumberForUpdate(first)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        Account b = accounts.findByAccountNumberForUpdate(second)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));

        Account source = request.fromAccount().equals(first) ? a : b;
        Account target = request.toAccount().equals(first) ? a : b;
        if (source.getOwnerEmail() == null || !source.getOwnerEmail().equals(requester)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }

        Optional<BankTransaction> existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            BankTransaction tx = existing.get();
            if (!tx.getFromAccount().equals(request.fromAccount())
                    || !tx.getToAccount().equals(request.toAccount())
                    || tx.getAmount().compareTo(request.amount()) != 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency-Key already used");
            }
            return toTransferResponse(tx);
        }

        BigDecimal amount = request.amount();
        if (source.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient funds");
        }

        source.debit(amount);
        target.credit(amount);
        BankTransaction tx = transactions.save(new BankTransaction(
                "TXN-" + UUID.randomUUID(), request.fromAccount(), request.toAccount(), amount, idempotencyKey));

        evictAfterCommit(
                source.getAccountNumber() + ":" + source.getOwnerEmail(),
                target.getAccountNumber() + ":" + target.getOwnerEmail());
        return toTransferResponse(tx);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> history(String accountNumber, String requester) {
        if (accounts.findByAccountNumberAndOwnerEmail(accountNumber, requester).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return transactions.findTop50ByFromAccountOrToAccountOrderByCreatedAtDesc(
                        accountNumber, accountNumber).stream().map(this::toTransferResponse).toList();
    }

    private void evictAfterCommit(String... cacheKeys) {
        Runnable evict = () -> {
            var cache = cacheManager.getCache("account-balance");
            if (cache != null) {
                for (String key : cacheKeys) cache.evict(key);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evict.run(); }
            });
        } else {
            evict.run();
        }
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(),
                account.getHolderName(), account.getBalance(), account.getVersion(), account.getCreatedAt());
    }

    private TransferResponse toTransferResponse(BankTransaction transaction) {
        return new TransferResponse(transaction.getReference(), transaction.getFromAccount(),
                transaction.getToAccount(), transaction.getAmount(), transaction.getStatus(),
                transaction.getCreatedAt());
    }
}
