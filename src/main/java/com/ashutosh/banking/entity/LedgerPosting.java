package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "ledger_postings",
       uniqueConstraints = @UniqueConstraint(name = "uk_ledger_reference_leg",
               columnNames = {"transaction_reference", "entry_type"}),
       indexes = {
               @Index(name = "idx_ledger_account", columnList = "account_number,created_at"),
               @Index(name = "idx_ledger_reference", columnList = "transaction_reference")
       })
public class LedgerPosting {
    public enum EntryType { DEBIT, CREDIT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, length = 40, updatable = false)
    private String transactionReference;

    @Column(name = "account_number", nullable = false, length = 24, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerPosting() {}

    public LedgerPosting(String reference, String accountNumber, EntryType entryType, BigDecimal amount) {
        if (reference == null || reference.isBlank() || accountNumber == null || accountNumber.isBlank()
                || entryType == null || amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid ledger posting");
        }
        this.transactionReference = reference;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTransactionReference() { return transactionReference; }
    public String getAccountNumber() { return accountNumber; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
