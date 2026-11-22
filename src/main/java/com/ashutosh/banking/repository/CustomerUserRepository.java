package com.ashutosh.banking.repository;

import com.ashutosh.banking.entity.CustomerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerUserRepository extends JpaRepository<CustomerUser, Long> {
    Optional<CustomerUser> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByVerifiedPhoneNumber(String phoneNumber);
}
