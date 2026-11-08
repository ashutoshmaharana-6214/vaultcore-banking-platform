package com.ashutosh.banking.repository;
import com.ashutosh.banking.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    List<BankTransaction> findTop50ByFromAccountOrToAccountOrderByCreatedAtDesc(String from, String to);
Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);
}
