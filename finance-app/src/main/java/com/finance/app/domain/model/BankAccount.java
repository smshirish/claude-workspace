package com.finance.app.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class BankAccount {

    private final AccountId accountId;
    private final String bankName;
    private final String accountNumber;
    private final AccountType accountType;
    private final BigDecimal balance;
    private final String currency;
    private final LocalDateTime importedAt;

    public BankAccount(AccountId accountId, String bankName, String accountNumber,
                       AccountType accountType, BigDecimal balance, String currency,
                       LocalDateTime importedAt) {
        this.accountId = Objects.requireNonNull(accountId, "accountId is required");
        this.bankName = Objects.requireNonNull(bankName, "bankName is required");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber is required");
        this.accountType = Objects.requireNonNull(accountType, "accountType is required");
        this.balance = Objects.requireNonNull(balance, "balance is required");
        this.currency = Objects.requireNonNull(currency, "currency is required");
        this.importedAt = Objects.requireNonNull(importedAt, "importedAt is required");
    }

    public static BankAccount create(String bankName, String accountNumber,
                                     AccountType accountType, BigDecimal balance, String currency) {
        return new BankAccount(AccountId.generate(), bankName, accountNumber,
                accountType, balance, currency, LocalDateTime.now());
    }

    public AccountId accountId() { return accountId; }
    public String bankName() { return bankName; }
    public String accountNumber() { return accountNumber; }
    public AccountType accountType() { return accountType; }
    public BigDecimal balance() { return balance; }
    public String currency() { return currency; }
    public LocalDateTime importedAt() { return importedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BankAccount that)) return false;
        return Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return "BankAccount{accountId=" + accountId + ", bankName=" + bankName
                + ", accountNumber=" + accountNumber + ", accountType=" + accountType + "}";
    }
}
