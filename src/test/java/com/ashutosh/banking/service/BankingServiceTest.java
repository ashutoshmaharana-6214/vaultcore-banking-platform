package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.CreateAccountRequest;
import com.ashutosh.banking.dto.TransferRequest;
import com.ashutosh.banking.entity.Account;
import com.ashutosh.banking.entity.BankTransaction;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import com.ashutosh.banking.repository.LedgerPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankingServiceTest {
    private static final String OWNER = "owner@example.com";
    private AccountRepository accounts;
    private BankTransactionRepository transactions;
    private Cache cache;
    private BankingService service;
    private LedgerPostingRepository ledgerPostings;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        transactions = mock(BankTransactionRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache("account-balance")).thenReturn(cache);
        when(transactions.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        ledgerPostings = mock(LedgerPostingRepository.class);
        service = new BankingService(accounts, transactions, cacheManager, ledgerPostings);
    }

    private void setupAccounts(Account source, Account target) {
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0001")).thenReturn(Optional.of(source));
        when(accounts.findByAccountNumberForUpdate("ACCOUNT0002")).thenReturn(Optional.of(target));
        when(transactions.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void transferMovesFundsAndEvictsOwnerSpecificCacheEntries() {
        Account source = new Account("ACCOUNT0001", "Source", new BigDecimal("500.00"), OWNER);
        Account target = new Account("ACCOUNT0002", "Target", new BigDecimal("100.00"), "other@example.com");
        setupAccounts(source, target);

        var result = service.transfer(new TransferRequest("ACCOUNT0001", "ACCOUNT0002",
                new BigDecimal("125.00")), "transfer-001", OWNER);

        assertEquals(0, new BigDecimal("375.00").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("225.00").compareTo(target.getBalance()));
        assertEquals("COMPLETED", result.status());
        verify(cache).evict("ACCOUNT0001:" + OWNER);
        verify(cache).evict("ACCOUNT0002:other@example.com");
        verify(ledgerPostings, times(2)).save(any(com.ashutosh.banking.entity.LedgerPosting.class));
    }

    @Test
    void transferRejectsUnownedSourceBeforeMutation() {
        Account source = new Account("ACCOUNT0001", "Source", new BigDecimal("500.00"), "other@example.com");
        Account target = new Account("ACCOUNT0002", "Target", new BigDecimal("100.00"), OWNER);
        setupAccounts(source, target);

        assertThrows(ApiException.class, () -> service.transfer(new TransferRequest(
                "ACCOUNT0001", "ACCOUNT0002", new BigDecimal("10.00")), "transfer-002", OWNER));
        assertEquals(0, new BigDecimal("500.00").compareTo(source.getBalance()));
        verify(transactions, never()).save(any());
        verify(ledgerPostings, never()).save(any());
    }

    @Test
    void insufficientFundsDoesNotSaveTransfer() {
        Account source = new Account("ACCOUNT0001", "Source", new BigDecimal("5.00"), OWNER);
        Account target = new Account("ACCOUNT0002", "Target", BigDecimal.ZERO, OWNER);
        setupAccounts(source, target);
        assertThrows(ApiException.class, () -> service.transfer(
                new TransferRequest("ACCOUNT0001", "ACCOUNT0002", new BigDecimal("10.00")),
                "transfer-003", OWNER));
        verify(transactions, never()).save(any());
    }

    @Test
    void duplicateKeyDoesNotDebitTwice() {
        Account source = new Account("ACCOUNT0001", "Source", new BigDecimal("500.00"), OWNER);
        Account target = new Account("ACCOUNT0002", "Target", BigDecimal.ZERO, OWNER);
        setupAccounts(source, target);
        TransferRequest request = new TransferRequest("ACCOUNT0001", "ACCOUNT0002", new BigDecimal("50.00"));
        var first = service.transfer(request, "transfer-004", OWNER);
        BankTransaction previous = new BankTransaction(first.reference(), request.fromAccount(),
                request.toAccount(), request.amount(), "transfer-004");
        when(transactions.findByIdempotencyKey("transfer-004")).thenReturn(Optional.of(previous));

        var second = service.transfer(request, "transfer-004", OWNER);
        assertEquals(first.reference(), second.reference());
        assertEquals(0, new BigDecimal("450.00").compareTo(source.getBalance()));
        verify(transactions, times(1)).save(any(BankTransaction.class));
        verify(ledgerPostings, times(2)).save(any(com.ashutosh.banking.entity.LedgerPosting.class));
    }

    @Test
    void reusedKeyForDifferentPayloadIsConflict() {
        Account source = new Account("ACCOUNT0001", "Source", new BigDecimal("500.00"), OWNER);
        Account target = new Account("ACCOUNT0002", "Target", BigDecimal.ZERO, OWNER);
        setupAccounts(source, target);
        when(transactions.findByIdempotencyKey("transfer-005")).thenReturn(
                Optional.of(new BankTransaction("TXN-existing", "ACCOUNT0001", "ACCOUNT0002",
                        new BigDecimal("50.00"), "transfer-005")));

        assertThrows(ApiException.class, () -> service.transfer(
                new TransferRequest("ACCOUNT0001", "ACCOUNT0002", new BigDecimal("100.00")),
                "transfer-005", OWNER));
        verify(transactions, never()).save(any());
    }

    @Test
    void accountReadsMustMatchAuthenticatedOwner() {
        when(accounts.findByAccountNumberAndOwnerEmail("ACCOUNT0001", OWNER)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.getAccount("ACCOUNT0001", OWNER));
    }

    @Test
    void openingBalanceCannotMintMoney() {
        assertThrows(ApiException.class, () -> service.createAccount(new CreateAccountRequest(
                "ACCOUNT0001", "Source", new BigDecimal("1000.00")), OWNER));
        verify(accounts, never()).save(any());
    }

    @Test
    void zeroBalanceAccountBindsToAuthenticatedOwner() {
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createAccount(new CreateAccountRequest(
                "ACCOUNT0001", "Source", BigDecimal.ZERO), OWNER);
        assertEquals("ACCOUNT0001", response.accountNumber());
        var captor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        assertEquals(OWNER, captor.getValue().getOwnerEmail());
    }
}
