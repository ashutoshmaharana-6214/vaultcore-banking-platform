package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(name = "uk_account_number", columnNames = "account_number"))
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_number", nullable = false, unique = true, length = 24) private String accountNumber;
    @Column(nullable = false, length = 120) private String holderName;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Version private long version;
    @Column(nullable = false) private Instant createdAt;

    protected Account() {}
    public Account(String accountNumber, String holderName, BigDecimal balance) {
        this.accountNumber = accountNumber; this.holderName = holderName; this.balance = balance; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public void debit(BigDecimal amount) { balance = balance.subtract(amount); }
    public void credit(BigDecimal amount) { balance = balance.add(amount); }
}
