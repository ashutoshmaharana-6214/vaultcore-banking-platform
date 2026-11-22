package com.ashutosh.banking.service;

import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.CustomerUserRepository;
import com.ashutosh.banking.sms.TwilioMessagingClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class PhoneVerificationService {
    private static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    private static final Duration VERIFY_COOLDOWN = Duration.ofSeconds(60);
    private final CustomerUserRepository users;
    private final StringRedisTemplate redis;
    private final TwilioMessagingClient twilio;

    public PhoneVerificationService(CustomerUserRepository users, StringRedisTemplate redis,
                                    TwilioMessagingClient twilio) {
        this.users = users;
        this.redis = redis;
        this.twilio = twilio;
    }

    private String userKey(String email) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return HexFormat.of().formatHex(digest);
    }

    public void startVerification(String email, String phone) {
        users.findByEmail(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (users.existsByVerifiedPhoneNumber(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "Number already registered");
        }
        String id = userKey(email);
        String cooldown = "vaultcore:phone:cooldown:" + id;
        String pending = "vaultcore:phone:pending:" + id;
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldown, "1", VERIFY_COOLDOWN))) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Verification already requested recently");
            }
            // Pending phone is short-lived; no account phone is saved until OTP is approved.
            redis.opsForValue().set(pending, phone, VERIFY_TTL);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException unavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Verification temporarily unavailable");
        }
        try {
            twilio.startVerification(phone);
        } catch (RuntimeException failure) {
            redis.delete(pending);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS verification provider unavailable");
        }
    }

    @Transactional
    public void confirmVerification(String email, String phone, String code) {
        String id = userKey(email);
        String pending = "vaultcore:phone:pending:" + id;
        String attempts = "vaultcore:phone:attempts:" + id;
        try {
            String waitingPhone = redis.opsForValue().get(pending);
            if (waitingPhone == null || !waitingPhone.equals(phone)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "No matching active verification request");
            }
            Long count = redis.opsForValue().increment(attempts);
            if (count == null) throw new IllegalStateException("Missing Redis verification counter");
            if (count == 1) redis.expire(attempts, VERIFY_TTL);
            if (count > 5) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException unavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Verification temporarily unavailable");
        }
        if (!twilio.checkVerification(phone, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }
        CustomerUser user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (users.existsByVerifiedPhoneNumber(phone) && !phone.equals(user.getVerifiedPhoneNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "Number already registered");
        }
        user.verifyPhoneNumber(phone);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException conflict) {
            throw new ApiException(HttpStatus.CONFLICT, "Number already registered");
        }
        // Do not delete verification state before DB transaction commits; TTL is short.
    }

    @Transactional
    public void setSmsAlertsEnabled(String email, boolean enabled) {
        CustomerUser user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (enabled && user.getVerifiedPhoneNumber() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Verify a phone number before enabling alerts");
        }
        user.setSmsAlertsEnabled(enabled);
    }
}
