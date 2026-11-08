package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.TransferRequest;
import com.ashutosh.banking.entity.Account;
import com.ashutosh.banking.entity.BankTransaction;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BankingServiceTest {
    private AccountRepository accounts;
    private BankTransactionRepository transactions;
    private CacheManager cacheManager;
    private Cache cache;
    private BankingService service;

    @BeforeEach void setUp() {
        accounts=mock(AccountRepository.class); transactions=mock(BankTransactionRepository.class);
        cacheManager=mock(CacheManager.class); cache=mock(Cache.class);
        when(cacheManager.getCache("account-balance")).thenReturn(cache);
        when(transactions.findByIdempotencyKey(anyString()))
        .thenReturn(Optional.empty());
        service=new BankingService(accounts,transactions,cacheManager);
    }

    @Test void transferMovesFundsAndEvictsBothAccountCacheEntries() {
        Account source=new Account("ACCOUNT0001","Source",new BigDecimal("500.00"));
        Account target=new Account("ACCOUNT0002","Target",new BigDecimal("100.00"));
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0001")).thenReturn(Optional.of(source));
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0002")).thenReturn(Optional.of(target));
        when(transactions.save(any(BankTransaction.class))).thenAnswer(invocation->invocation.getArgument(0));

   var response = service.transfer(
    new TransferRequest("ACCOUNT0001", "ACCOUNT0002", new BigDecimal("125.00")),
    "test-transfer-001"
);

        assertEquals(new BigDecimal("375.00"),source.getBalance());
        assertEquals(new BigDecimal("225.00"),target.getBalance());
        assertEquals("COMPLETED",response.status());
        verify(cache).evict("ACCOUNT0001"); verify(cache).evict("ACCOUNT0002");
        verify(transactions).save(any(BankTransaction.class));
    }

    @Test void transferRejectsInsufficientFundsWithoutSavingTransaction() {
        Account source=new Account("ACCOUNT0001","Source",new BigDecimal("20.00"));
        Account target=new Account("ACCOUNT0002","Target",new BigDecimal("10.00"));
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0001")).thenReturn(Optional.of(source));
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0002")).thenReturn(Optional.of(target));

  assertThrows(
    ApiException.class,
    () -> service.transfer(
        new TransferRequest(
            "ACCOUNT0001",
            "ACCOUNT0002",
            new BigDecimal("30.00")
        ),
        "test-transfer-002"
    )
);
        assertEquals(new BigDecimal("20.00"),source.getBalance());
        assertEquals(new BigDecimal("10.00"),target.getBalance());
        verify(transactions,never()).save(any());
    }

@Test
void retryWithSameIdempotencyKeyDoesNotDebitTwice() {
    Account source = new Account(
            "ACCOUNT0001", "Source", new BigDecimal("500.00"));
    Account target = new Account(
            "ACCOUNT0002", "Target", new BigDecimal("100.00"));

    when(accounts.findByAccountNumberForUpdate("ACCOUNT0001"))
            .thenReturn(Optional.of(source));
    when(accounts.findByAccountNumberForUpdate("ACCOUNT0002"))
            .thenReturn(Optional.of(target));

    when(transactions.save(any(BankTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    TransferRequest request = new TransferRequest(
            "ACCOUNT0001", "ACCOUNT0002", new BigDecimal("50.00"));

    var first = service.transfer(request, "repeat-test-001");

    BankTransaction savedTransaction =
            new BankTransaction(
                    first.reference(),
                    request.fromAccount(),
                    request.toAccount(),
                    request.amount(),
                    "repeat-test-001");

    when(transactions.findByIdempotencyKey("repeat-test-001"))
            .thenReturn(Optional.of(savedTransaction));

    var second = service.transfer(request, "repeat-test-001");

    assertEquals(first.reference(), second.reference());
    assertEquals(new BigDecimal("450.00"), source.getBalance());
    assertEquals(new BigDecimal("150.00"), target.getBalance());

    verify(transactions, times(1))
            .save(any(BankTransaction.class));
}

@Test
void sameIdempotencyKeyWithDifferentAmountIsRejected() {
    Account source = new Account(
            "ACCOUNT0001", "Source", new BigDecimal("500.00"));
    Account target = new Account(
            "ACCOUNT0002", "Target", new BigDecimal("100.00"));

    when(accounts.findByAccountNumberForUpdate("ACCOUNT0001"))
            .thenReturn(Optional.of(source));
    when(accounts.findByAccountNumberForUpdate("ACCOUNT0002"))
            .thenReturn(Optional.of(target));

    BankTransaction existing = new BankTransaction(
            "TXN-existing-001",
            "ACCOUNT0001",
            "ACCOUNT0002",
            new BigDecimal("50.00"),
            "conflict-test-001"
    );

    when(transactions.findByIdempotencyKey("conflict-test-001"))
            .thenReturn(Optional.of(existing));

    TransferRequest conflictingRequest = new TransferRequest(
            "ACCOUNT0001",
            "ACCOUNT0002",
            new BigDecimal("100.00")
    );

    assertThrows(
            ApiException.class,
            () -> service.transfer(
                    conflictingRequest,
                    "conflict-test-001"
            )
    );

    assertEquals(new BigDecimal("500.00"), source.getBalance());
    assertEquals(new BigDecimal("100.00"), target.getBalance());

    verify(transactions, never()).save(any(BankTransaction.class));
}

}
