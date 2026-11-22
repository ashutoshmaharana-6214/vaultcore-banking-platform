package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(name = "uk_account_number", columnNames = "account_number"))
public class Account {
    public enum AccountType { CUSTOMER, SYSTEM }

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 12)
    private AccountType accountType;

    public static Account systemAccount(String number) {
        Account result = new Account(number, "Sandbox Clearing Account", BigDecimal.ZERO, null);
        result.accountType = AccountType.SYSTEM;
        return result;
    }

    public AccountType getAccountType() { return accountType; }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 24)
    private String accountNumber;

    @Column(nullable = false, length = 120)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // Nullable only to allow existing demo data to migrate without assigning it to an unknown user.
    // Legacy unowned accounts are inaccessible through authenticated APIs.
    @Column(name = "owner_email", length = 254)
    private String ownerEmail;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    protected Account() {}

    public Account(String accountNumber, String holderName, BigDecimal balance, String ownerEmail) {
        this.accountType = AccountType.CUSTOMER;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = balance;
        this.ownerEmail = ownerEmail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public String getOwnerEmail() { return ownerEmail; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public void debit(BigDecimal amount) { balance = balance.subtract(amount); }
    public void credit(BigDecimal amount) { balance = balance.add(amount); }
}
