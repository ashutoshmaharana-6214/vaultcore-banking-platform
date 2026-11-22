package com.ashutosh.banking.integration;

import com.ashutosh.banking.dto.TransferRequest;
import com.ashutosh.banking.entity.Account;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import com.ashutosh.banking.service.BankingService;
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
@SpringBootTest(properties = {
        "vaultcore.jwt.secret=0123456789abcdef0123456789abcdef",
        "spring.jpa.hibernate.ddl-auto=create-drop"
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
}
