package com.ashutosh.banking.security;

import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {
    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = config.jwtSigningKey("0123456789abcdef0123456789abcdef");

    private String sign(String issuer, List<String> audience, Instant expiration) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user@example.com")
                .audience(audience)
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(expiration)
                .build();
        return config.jwtEncoder(key).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void rejectsShortJwtSecret() {
        assertThrows(IllegalStateException.class, () -> config.jwtSigningKey("test-secret"));
    }

    @Test
    void acceptsValidIssuerAndAudience() {
        String token = sign("vaultcore-local", List.of("vaultcore-api"), Instant.now().plusSeconds(120));
        assertEquals("user@example.com", config.jwtDecoder(key, "vaultcore-local").decode(token).getSubject());
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        String token = sign("vaultcore-local", List.of("other-api"), Instant.now().plusSeconds(120));
        assertThrows(JwtException.class, () -> config.jwtDecoder(key, "vaultcore-local").decode(token));
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        String token = sign("other-issuer", List.of("vaultcore-api"), Instant.now().plusSeconds(120));
        assertThrows(JwtException.class, () -> config.jwtDecoder(key, "vaultcore-local").decode(token));
    }

    @Test
    void rejectsExpiredToken() {
        String token = sign("vaultcore-local", List.of("vaultcore-api"), Instant.now().minusSeconds(120));
        assertThrows(JwtException.class, () -> config.jwtDecoder(key, "vaultcore-local").decode(token));
    }
}
