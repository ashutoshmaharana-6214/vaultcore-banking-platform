package com.ashutosh.banking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customer_users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_customer_email", columnNames = "email"),
    @UniqueConstraint(name = "uk_customer_verified_phone", columnNames = "verified_phone_number")
})
public class CustomerUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // This column must only be populated after successful provider OTP verification.
    @Column(name = "verified_phone_number", unique = true, length = 16)
    private String verifiedPhoneNumber;

    @Column(name = "sms_alerts_enabled", nullable = false)
    private boolean smsAlertsEnabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected CustomerUser() {}

    public CustomerUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.smsAlertsEnabled = false;
    }

    public void verifyPhoneNumber(String e164Number) {
        this.verifiedPhoneNumber = e164Number;
        // Consent is separate from confirming ownership.
        this.smsAlertsEnabled = false;
    }

    public void setSmsAlertsEnabled(boolean enabled) {
        if (enabled && verifiedPhoneNumber == null) {
            throw new IllegalStateException("Phone must be verified before enabling SMS alerts");
        }
        this.smsAlertsEnabled = enabled;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getVerifiedPhoneNumber() { return verifiedPhoneNumber; }
    public boolean isSmsAlertsEnabled() { return smsAlertsEnabled; }
    public Instant getCreatedAt() { return createdAt; }
}
