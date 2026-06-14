package com.finance.app.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class BankAccountTest {

    // T1.1
    @Test
    void create_generatesNonNullAccountIdAndImportedAt() {
        var account = BankAccount.create("ING", "NL91ABNA0417164300", AccountType.CHECKING, new BigDecimal("1500.00"), "EUR");

        assertThat(account.accountId()).isNotNull();
        assertThat(account.importedAt()).isNotNull().isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void create_setsAllSuppliedFields() {
        var account = BankAccount.create("Rabobank", "NL20INGB0001234567", AccountType.SAVINGS, new BigDecimal("250.75"), "EUR");

        assertThat(account.bankName()).isEqualTo("Rabobank");
        assertThat(account.accountNumber()).isEqualTo("NL20INGB0001234567");
        assertThat(account.accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.balance()).isEqualByComparingTo("250.75");
        assertThat(account.currency()).isEqualTo("EUR");
    }

    // T1.2 — null guards per field
    @Test
    void constructor_withNullBankName_throwsNpe() {
        assertThatThrownBy(() ->
                new BankAccount(AccountId.generate(), null, "ACC1", AccountType.CHECKING,
                        BigDecimal.ZERO, "EUR", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullAccountNumber_throwsNpe() {
        assertThatThrownBy(() ->
                new BankAccount(AccountId.generate(), "ING", null, AccountType.CHECKING,
                        BigDecimal.ZERO, "EUR", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullAccountType_throwsNpe() {
        assertThatThrownBy(() ->
                new BankAccount(AccountId.generate(), "ING", "ACC1", null,
                        BigDecimal.ZERO, "EUR", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullBalance_throwsNpe() {
        assertThatThrownBy(() ->
                new BankAccount(AccountId.generate(), "ING", "ACC1", AccountType.CHECKING,
                        null, "EUR", LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullCurrency_throwsNpe() {
        assertThatThrownBy(() ->
                new BankAccount(AccountId.generate(), "ING", "ACC1", AccountType.CHECKING,
                        BigDecimal.ZERO, null, LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    // T1.3
    @Test
    void equals_basedOnAccountIdOnly() {
        var id = AccountId.generate();
        var a1 = new BankAccount(id, "ING", "ACC1", AccountType.CHECKING, BigDecimal.ZERO, "EUR", LocalDateTime.now());
        var a2 = new BankAccount(id, "Rabobank", "ACC2", AccountType.SAVINGS, BigDecimal.ONE, "USD", LocalDateTime.now());

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void equals_withDifferentAccountIds_returnsFalse() {
        var a1 = BankAccount.create("ING", "ACC1", AccountType.CHECKING, BigDecimal.ZERO, "EUR");
        var a2 = BankAccount.create("ING", "ACC1", AccountType.CHECKING, BigDecimal.ZERO, "EUR");

        assertThat(a1).isNotEqualTo(a2);
    }
}
