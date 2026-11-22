package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.LoginRequest;
import com.ashutosh.banking.dto.RegisterRequest;
import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.CustomerUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private CustomerUserRepository users;
    private PasswordEncoder passwords;
    private JwtEncoder jwtEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(CustomerUserRepository.class);
        passwords = mock(PasswordEncoder.class);
        jwtEncoder = mock(JwtEncoder.class);
        service = new AuthService(users, passwords, jwtEncoder, "vaultcore-local");
    }

    @Test
    void registrationNormalizesEmailAndHashesPassword() {
        when(passwords.encode(anyString())).thenReturn("bcrypt-hash");
        service.register(new RegisterRequest(" TEST@Example.COM ", "long-test-password"));
        var captor = org.mockito.ArgumentCaptor.forClass(CustomerUser.class);
        verify(users).save(captor.capture());
        assertEquals("test@example.com", captor.getValue().getEmail());
        assertEquals("bcrypt-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void loginRejectsIncorrectPassword() {
        when(users.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(new CustomerUser("demo@example.com", "bcrypt-hash")));
        when(passwords.matches("wrong-password", "bcrypt-hash")).thenReturn(false);
        assertThrows(ApiException.class, () -> service.login(
                new LoginRequest("demo@example.com", "wrong-password")));
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void duplicateRegistrationIsRejected() {
        when(users.existsByEmail("demo@example.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.register(
                new RegisterRequest("demo@example.com", "long-test-password")));
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsShortLivedBearerToken() {
        when(users.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(new CustomerUser("demo@example.com", "bcrypt-hash")));
        when(passwords.matches("correct-password", "bcrypt-hash")).thenReturn(true);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("signed-test-token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        var token = service.login(new LoginRequest("DEMO@example.com", "correct-password"));
        assertEquals("Bearer", token.tokenType());
        assertEquals("signed-test-token", token.accessToken());
        assertEquals(900, token.expiresInSeconds());
    }
}
