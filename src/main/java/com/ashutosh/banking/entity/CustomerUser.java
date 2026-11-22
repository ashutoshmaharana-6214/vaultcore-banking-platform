package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customer_users", uniqueConstraints = @UniqueConstraint(name = "uk_customer_email", columnNames = "email"))
public class CustomerUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    protected CustomerUser() {}

    public CustomerUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
