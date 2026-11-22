package com.ashutosh.banking.sandbox;

import com.ashutosh.banking.entity.Account;
import com.ashutosh.banking.entity.BankTransaction;
import com.ashutosh.banking.entity.LedgerPosting;
import com.ashutosh.banking.repository.AccountRepository;
import com.ashutosh.banking.repository.BankTransactionRepository;
import com.ashutosh.banking.repository.CustomerUserRepository;
import com.ashutosh.banking.repository.LedgerPostingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Only loaded in an explicitly enabled, isolated sandbox-seed profile. */
@Service
@Profile("sandbox-seed")
public class SandboxFundingService {
    public static final String TREASURY_ACCOUNT = "VC-SANDBOX-TREASURY";
    public static final BigDecimal FUNDING_AMOUNT = new BigDecimal("100.00");

    private final AccountRepository accounts;
    private final BankTransactionRepository transfers;
    private final LedgerPostingRepository postings;
    private final CustomerUserRepository users;

    public SandboxFundingService(AccountRepository accounts, BankTransactionRepository transfers,
                                 LedgerPostingRepository postings, CustomerUserRepository users) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.postings = postings;
        this.users = users;
    }

    public static String sourceNumber(int pair) { return "BSRC%08d".formatted(pair); }
    public static String targetNumber(int pair) { return "BDST%08d".formatted(pair); }

    @Transactional
    public void ensureTreasury() {
        var existing = accounts.findByAccountNumber(TREASURY_ACCOUNT);
        if (existing.isPresent()) {
            Account treasury = existing.get();
            if (treasury.getAccountType() != Account.AccountType.SYSTEM || treasury.getOwnerEmail() != null) {
                throw new IllegalStateException("Synthetic treasury name conflicts with customer data");
            }
        } else {
            accounts.saveAndFlush(Account.systemAccount(TREASURY_ACCOUNT));
        }
    }

    @Transactional
    public void provisionPair(int index, String owner) {
        validate(index, owner);
        provision(sourceNumber(index), owner);
        provision(targetNumber(index), owner);
    }

    private void provision(String number, String owner) {
        var existing = accounts.findByAccountNumber(number);
        if (existing.isPresent()) {
            Account account = existing.get();
            if (account.getAccountType() != Account.AccountType.CUSTOMER
                    || !owner.equals(account.getOwnerEmail())) {
                throw new IllegalStateException("Synthetic fixture account belongs to another owner");
            }
            return;
        }
        accounts.save(new Account(number, "Synthetic Test Account", BigDecimal.ZERO, owner));
    }

    private void validate(int index, String owner) {
        if (index < 1 || index > 10000 || owner == null || owner.isBlank()
                || !users.existsByEmail(owner)) {
            throw new IllegalStateException("Seed requires 1..10000 and an existing test-only customer");
        }
    }

    /** Atomic funding transaction with a balanced double-entry trail. */
    @Transactional
    public String fundSource(int index, String owner) {
        validate(index, owner);
        String destination = sourceNumber(index);
        String first = destination.compareTo(TREASURY_ACCOUNT) < 0 ? destination : TREASURY_ACCOUNT;
        String second = first.equals(destination) ? TREASURY_ACCOUNT : destination;
        Account a = accounts.findByAccountNumberForUpdate(first)
                .orElseThrow(() -> new IllegalStateException("Synthetic account missing"));
        Account b = accounts.findByAccountNumberForUpdate(second)
                .orElseThrow(() -> new IllegalStateException("Treasury missing"));
        Account treasury = first.equals(TREASURY_ACCOUNT) ? a : b;
        Account customer = first.equals(destination) ? a : b;

        if (treasury.getAccountType() != Account.AccountType.SYSTEM
                || treasury.getOwnerEmail() != null
                || customer.getAccountType() != Account.AccountType.CUSTOMER
                || !owner.equals(customer.getOwnerEmail())) {
            throw new IllegalStateException("Cannot fund arbitrary accounts");
        }

        String key = "sandbox-funding-v1-" + index;
        Optional<BankTransaction> existing = transfers.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            BankTransaction previous = existing.get();
            if (!TREASURY_ACCOUNT.equals(previous.getFromAccount())
                    || !destination.equals(previous.getToAccount())
                    || previous.getAmount().compareTo(FUNDING_AMOUNT) != 0) {
                throw new IllegalStateException("Funding idempotency conflict");
            }
            return previous.getReference();
        }

        treasury.debit(FUNDING_AMOUNT);
        customer.credit(FUNDING_AMOUNT);
        BankTransaction tx = transfers.save(new BankTransaction(
                "SEED-" + UUID.randomUUID().toString().replace("-", ""), TREASURY_ACCOUNT, destination, FUNDING_AMOUNT, key));
        postings.save(new LedgerPosting(
                tx.getReference(), TREASURY_ACCOUNT, LedgerPosting.EntryType.DEBIT, FUNDING_AMOUNT));
        postings.save(new LedgerPosting(
                tx.getReference(), destination, LedgerPosting.EntryType.CREDIT, FUNDING_AMOUNT));
        return tx.getReference();
    }
}
