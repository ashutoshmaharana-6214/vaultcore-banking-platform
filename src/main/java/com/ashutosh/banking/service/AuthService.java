package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.*;
import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.CustomerUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private final CustomerUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder encoder;
    private final String issuer;

    public AuthService(CustomerUserRepository users, PasswordEncoder passwordEncoder,
                       JwtEncoder encoder, @Value("${vaultcore.jwt.issuer}") String issuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.encoder = encoder;
        this.issuer = issuer;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Account already registered");
        }
        users.save(new CustomerUser(email, passwordEncoder.encode(request.password())));
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse login(LoginRequest request) {
        String email = normalize(request.email());
        CustomerUser user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .audience(List.of("vaultcore-api"))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AccessTokenResponse(token, "Bearer", ACCESS_TOKEN_TTL.toSeconds());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
