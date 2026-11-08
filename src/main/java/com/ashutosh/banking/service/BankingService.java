package com.ashutosh.banking.service;
import com.ashutosh.banking.dto.*;
import com.ashutosh.banking.entity.*;
import com.ashutosh.banking.exception.ApiException;
import com.ashutosh.banking.repository.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.*;
@Service
public class BankingService {
 private final AccountRepository accounts; private final BankTransactionRepository transactions; private final CacheManager cacheManager;
 public BankingService(AccountRepository accounts,BankTransactionRepository transactions,CacheManager cacheManager){this.accounts=accounts;this.transactions=transactions;this.cacheManager=cacheManager;}
 @Transactional public AccountResponse createAccount(CreateAccountRequest request){
  if(accounts.findByAccountNumber(request.accountNumber()).isPresent()) throw new ApiException(HttpStatus.CONFLICT,"Account number already exists");
  Account saved=accounts.save(new Account(request.accountNumber(),request.holderName(),request.openingBalance())); return toResponse(saved);
 }
 @Transactional(readOnly=true) @Cacheable(cacheNames="account-balance",key="#accountNumber")
 public AccountResponse getAccount(String accountNumber){return accounts.findByAccountNumber(accountNumber).map(this::toResponse).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Account not found"));}
 @Transactional
public TransferResponse transfer(
        TransferRequest request,
        String idempotencyKey) {

  if (idempotencyKey == null ||
        !idempotencyKey.matches("[A-Za-z0-9._:-]{1,100}")) {
    throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "Valid Idempotency-Key is required"
    );
}
if(request.fromAccount().equals(request.toAccount())) throw new ApiException(HttpStatus.BAD_REQUEST,"Source and destination accounts must be different");
  String first=request.fromAccount().compareTo(request.toAccount())<0?request.fromAccount():request.toAccount();
  String second=first.equals(request.fromAccount())?request.toAccount():request.fromAccount();
  Account a=accounts.findByAccountNumberForUpdate(first).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Account not found: "+first));
  Account b=accounts.findByAccountNumberForUpdate(second).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Account not found: "+second));
  Account source=request.fromAccount().equals(first)?a:b; Account target=request.toAccount().equals(first)?a:b;

Optional<BankTransaction> existing =
        transactions.findByIdempotencyKey(idempotencyKey);

if (existing.isPresent()) {
    BankTransaction tx = existing.get();

    if (!tx.getFromAccount().equals(request.fromAccount())
            || !tx.getToAccount().equals(request.toAccount())
            || tx.getAmount().compareTo(request.amount()) != 0) {

        throw new ApiException(
                HttpStatus.CONFLICT,
                "Idempotency-Key already used for a different transfer"
        );
    }

    return new TransferResponse(
            tx.getReference(),
            tx.getFromAccount(),
            tx.getToAccount(),
            tx.getAmount(),
            tx.getStatus(),
            tx.getCreatedAt()
    );
}

  BigDecimal amount=request.amount();
  if(source.getBalance().compareTo(amount)<0) throw new ApiException(HttpStatus.CONFLICT,"Insufficient funds");
  source.debit(amount); target.credit(amount);

String reference = "TXN-" + UUID.randomUUID();

BankTransaction tx = transactions.save(
    new BankTransaction(
        reference,
        request.fromAccount(),
        request.toAccount(),
        amount,
        idempotencyKey
    )
);

  evictAfterCommit(request.fromAccount(),request.toAccount());
  return new TransferResponse(tx.getReference(),tx.getFromAccount(),tx.getToAccount(),tx.getAmount(),tx.getStatus(),tx.getCreatedAt());
 }
 @Transactional(readOnly=true) public List<TransferResponse> history(String accountNumber){
  if(accounts.findByAccountNumber(accountNumber).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Account not found");
  return transactions.findTop50ByFromAccountOrToAccountOrderByCreatedAtDesc(accountNumber,accountNumber).stream().map(t->new TransferResponse(t.getReference(),t.getFromAccount(),t.getToAccount(),t.getAmount(),t.getStatus(),t.getCreatedAt())).toList();
 }
 private void evictAfterCommit(String... numbers){
  Runnable evict=()->{var cache=cacheManager.getCache("account-balance");if(cache!=null)for(String number:numbers)cache.evict(number);};
  if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){evict.run();}}); else evict.run();
 }
 private AccountResponse toResponse(Account a){return new AccountResponse(a.getId(),a.getAccountNumber(),a.getHolderName(),a.getBalance(),a.getVersion(),a.getCreatedAt());}
}
