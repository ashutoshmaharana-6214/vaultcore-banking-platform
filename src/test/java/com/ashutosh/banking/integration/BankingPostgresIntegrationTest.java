package com.ashutosh.banking.integration;

import com.ashutosh.banking.dto.TransferRequest;
import com.ashutosh.banking.entity.Account;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import com.ashutosh.banking.repository.LedgerPostingRepository;
import com.ashutosh.banking.entity.LedgerPosting;
import com.ashutosh.banking.service.BankingService;
import com.ashutosh.banking.sandbox.SandboxFundingService;
import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.repository.CustomerUserRepository;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@ActiveProfiles("sandbox-seed")
@SpringBootTest(properties = {
        "vaultcore.jwt.secret=0123456789abcdef0123456789abcdef",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class BankingPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private BankingService service;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private BankTransactionRepository transactions;

    @Autowired
    private LedgerPostingRepository ledgerPostings;

    @Autowired
    private SandboxFundingService sandboxFunding;

    @Autowired
    private CustomerUserRepository users;

    private String newAccountNumber() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    @Test
    void concurrentRetriesTransferMoneyExactlyOnceInRealPostgres() throws Exception {
        String owner = "load-test@example.com";
        String from = newAccountNumber();
        String to = newAccountNumber();
        accounts.saveAndFlush(new Account(from, "Source", new BigDecimal("1000.00"), owner));
        accounts.saveAndFlush(new Account(to, "Target", new BigDecimal("100.00"), owner));

        var request = new TransferRequest(from, to, new BigDecimal("125.00"));
        String key = "it-" + UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> service.transfer(request, key, owner));
            var b = pool.submit(() -> service.transfer(request, key, owner));
            var first = a.get(30, TimeUnit.SECONDS);
            var second = b.get(30, TimeUnit.SECONDS);

            assertEquals(first.reference(), second.reference());
            assertEquals(0, accounts.findByAccountNumber(from).orElseThrow().getBalance()
                    .compareTo(new BigDecimal("875.00")));
            assertEquals(0, accounts.findByAccountNumber(to).orElseThrow().getBalance()
                    .compareTo(new BigDecimal("225.00")));
            assertEquals(1, transactions.findByIdempotencyKey(key).stream().count());
            var entries = ledgerPostings.findByTransactionReferenceOrderByIdAsc(first.reference());
            assertEquals(2, entries.size());
            assertEquals(1, entries.stream().filter(e -> e.getEntryType() == LedgerPosting.EntryType.DEBIT).count());
            assertEquals(1, entries.stream().filter(e -> e.getEntryType() == LedgerPosting.EntryType.CREDIT).count());
            assertEquals(0, entries.get(0).getAmount().compareTo(entries.get(1).getAmount()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void transferFromAnotherUsersAccountFails() {
        String from = newAccountNumber();
        String to = newAccountNumber();
        accounts.saveAndFlush(new Account(from, "Private", new BigDecimal("100.00"), "one@example.com"));
        accounts.saveAndFlush(new Account(to, "Recipient", BigDecimal.ZERO, "two@example.com"));

        assertThrows(ApiException.class, () -> service.transfer(
                new TransferRequest(from, to, new BigDecimal("25.00")),
                "forbidden-" + UUID.randomUUID(), "attacker@example.com"));
        assertEquals(0, accounts.findByAccountNumber(from).orElseThrow().getBalance()
                .compareTo(new BigDecimal("100.00")));
    }

    @Test
    void thirtyTwoParallelTransfersAcrossIndependentAccountsPreserveLedger() throws Exception {
        final int transferCount = 32;
        final String owner = "parallel-load@example.com";
        java.util.List<TransferRequest> requests = new java.util.ArrayList<>();
        for (int i = 0; i < transferCount; i++) {
            String source = newAccountNumber();
            String destination = newAccountNumber();
            accounts.saveAndFlush(new Account(source, "Load Source", new BigDecimal("100.00"), owner));
            accounts.saveAndFlush(new Account(destination, "Load Destination", BigDecimal.ZERO, owner));
            requests.add(new TransferRequest(source, destination, new BigDecimal("10.00")));
        }

        var workers = Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<com.ashutosh.banking.dto.TransferResponse>> results =
                    new java.util.ArrayList<>();
            for (int i = 0; i < transferCount; i++) {
                final int index = i;
                results.add(workers.submit(() -> service.transfer(requests.get(index),
                        "parallel-" + UUID.randomUUID(), owner)));
            }

            for (int i = 0; i < transferCount; i++) {
                var response = results.get(i).get(45, TimeUnit.SECONDS);
                var request = requests.get(i);
                assertEquals("COMPLETED", response.status());
                assertEquals(0, accounts.findByAccountNumber(request.fromAccount())
                        .orElseThrow().getBalance().compareTo(new BigDecimal("90.00")));
                assertEquals(0, accounts.findByAccountNumber(request.toAccount())
                        .orElseThrow().getBalance().compareTo(new BigDecimal("10.00")));

                var postings = ledgerPostings.findByTransactionReferenceOrderByIdAsc(response.reference());
                assertEquals(2, postings.size(), "Every successful transfer needs two journal postings");
                assertEquals(1, postings.stream()
                        .filter(p -> p.getEntryType() == LedgerPosting.EntryType.DEBIT
                                && p.getAccountNumber().equals(request.fromAccount())
                                && p.getAmount().compareTo(request.amount()) == 0).count());
                assertEquals(1, postings.stream()
                        .filter(p -> p.getEntryType() == LedgerPosting.EntryType.CREDIT
                                && p.getAccountNumber().equals(request.toAccount())
                                && p.getAmount().compareTo(request.amount()) == 0).count());
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void concurrentTransfersOnSameAccountsNeverOverdrawOrLosePostings() throws Exception {
        final int transferCount = 12;
        final String owner = "contended-load@example.com";
        String from = newAccountNumber();
        String to = newAccountNumber();
        accounts.saveAndFlush(new Account(from, "Contended Source", new BigDecimal("20.00"), owner));
        accounts.saveAndFlush(new Account(to, "Contended Destination", BigDecimal.ZERO, owner));

        var workers = Executors.newFixedThreadPool(6);
        try {
            java.util.List<java.util.concurrent.Future<com.ashutosh.banking.dto.TransferResponse>> results =
                    new java.util.ArrayList<>();
            for (int i = 0; i < transferCount; i++) {
                final String idempotencyKey = "contended-" + UUID.randomUUID();
                results.add(workers.submit(() -> service.transfer(
                        new TransferRequest(from, to, BigDecimal.ONE), idempotencyKey, owner)));
            }

            for (var result : results) {
                var tx = result.get(45, TimeUnit.SECONDS);
                assertEquals("COMPLETED", tx.status());
                assertEquals(2, ledgerPostings.findByTransactionReferenceOrderByIdAsc(tx.reference()).size());
            }
            assertEquals(0, accounts.findByAccountNumber(from).orElseThrow().getBalance()
                    .compareTo(new BigDecimal("8.00")));
            assertEquals(0, accounts.findByAccountNumber(to).orElseThrow().getBalance()
                    .compareTo(new BigDecimal("12.00")));
        } finally {
            workers.shutdownNow();
        }
    }


    @Test
    void sandboxFundingIsBalancedAndIdempotent() {
        String user = "seed-" + UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new CustomerUser(user, "synthetic-test-hash"));
        sandboxFunding.ensureTreasury();
        sandboxFunding.provisionPair(1, user);

        String first = sandboxFunding.fundSource(1, user);
        String replay = sandboxFunding.fundSource(1, user);
        assertEquals(first, replay);

        var source = accounts.findByAccountNumber(SandboxFundingService.sourceNumber(1)).orElseThrow();
        var treasury = accounts.findByAccountNumber(SandboxFundingService.TREASURY_ACCOUNT).orElseThrow();
        assertEquals(0, source.getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, treasury.getBalance().compareTo(new BigDecimal("-100.00")));
        assertEquals(Account.AccountType.SYSTEM, treasury.getAccountType());
        assertEquals(2, ledgerPostings.findByTransactionReferenceOrderByIdAsc(first).size());
        assertEquals(1, transactions.findByIdempotencyKey("sandbox-funding-v1-1").stream().count());

        // A customer cannot submit a transfer TO the system treasury.
        assertThrows(ApiException.class, () -> service.transfer(
                new TransferRequest(SandboxFundingService.sourceNumber(1),
                        SandboxFundingService.TREASURY_ACCOUNT, BigDecimal.ONE),
                "must-reject-" + UUID.randomUUID(), user));
    }

}
