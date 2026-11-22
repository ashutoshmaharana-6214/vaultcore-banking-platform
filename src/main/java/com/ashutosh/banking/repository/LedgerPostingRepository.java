package com.ashutosh.banking.repository;

import com.ashutosh.banking.entity.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, Long> {
    List<LedgerPosting> findByTransactionReferenceOrderByIdAsc(String transactionReference);
}
