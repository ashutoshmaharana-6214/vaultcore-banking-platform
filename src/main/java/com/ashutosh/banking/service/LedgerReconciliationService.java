package com.ashutosh.banking.service;

import com.ashutosh.banking.dto.LedgerReconciliationResponse;
import com.ashutosh.banking.entity.BankTransaction;
import com.ashutosh.banking.entity.LedgerPosting;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import com.ashutosh.banking.repository.LedgerPostingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerReconciliationService {
    private final BankTransactionRepository transfers;
    private final LedgerPostingRepository postings;
    private final AccountRepository accounts;

    public LedgerReconciliationService(BankTransactionRepository transfers,
                                       LedgerPostingRepository postings,
                                       AccountRepository accounts) {
        this.transfers = transfers;
        this.postings = postings;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public LedgerReconciliationResponse reconcile(String reference, String requester) {
        BankTransaction tx = transfers.findByReference(reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));

        boolean allowed = accounts.findByAccountNumberAndOwnerEmail(tx.getFromAccount(), requester).isPresent()
                || accounts.findByAccountNumberAndOwnerEmail(tx.getToAccount(), requester).isPresent();
        if (!allowed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found");
        }

        List<LedgerPosting> entries = postings.findByTransactionReferenceOrderByIdAsc(reference);
        BigDecimal net = entries.stream()
                .map(p -> p.getEntryType() == LedgerPosting.EntryType.DEBIT
                        ? p.getAmount().negate() : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Zero-sum alone is not sufficient: check both account identities and exact amounts.
        boolean balanced = entries.size() == 2
                && net.compareTo(BigDecimal.ZERO) == 0
                && entries.stream().anyMatch(p -> p.getEntryType() == LedgerPosting.EntryType.DEBIT
                        && p.getAccountNumber().equals(tx.getFromAccount())
                        && p.getAmount().compareTo(tx.getAmount()) == 0)
                && entries.stream().anyMatch(p -> p.getEntryType() == LedgerPosting.EntryType.CREDIT
                        && p.getAccountNumber().equals(tx.getToAccount())
                        && p.getAmount().compareTo(tx.getAmount()) == 0);
        return new LedgerReconciliationResponse(reference, entries.size(), net, balanced);
    }
}
