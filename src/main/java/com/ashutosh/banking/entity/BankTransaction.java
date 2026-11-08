package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bank_transactions", indexes = @Index(name = "idx_transaction_reference", columnList = "reference"))
public class BankTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String reference;
    @Column(name = "idempotency_key", unique = true, length = 100)
private String idempotencyKey;
    @Column(nullable = false, length = 24) private String fromAccount;
    @Column(nullable = false, length = 24) private String toAccount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private Instant createdAt;
    protected BankTransaction() {}
    public BankTransaction(String reference, String fromAccount, String toAccount, BigDecimal amount) {
        this.reference=reference; this.fromAccount=fromAccount; this.toAccount=toAccount; this.amount=amount; this.status="COMPLETED"; this.createdAt=Instant.now();
    }
    public Long getId(){return id;} public String getReference(){return reference;} public String getFromAccount(){return fromAccount;}
    public BankTransaction(
        String reference,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String idempotencyKey) {

    this(reference, fromAccount, toAccount, amount);
    this.idempotencyKey = idempotencyKey;
}
    public String getToAccount(){return toAccount;} public BigDecimal getAmount(){return amount;} public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
public String getIdempotencyKey() {
    return idempotencyKey;
}
}
