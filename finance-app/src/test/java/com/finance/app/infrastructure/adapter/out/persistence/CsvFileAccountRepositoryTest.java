package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CsvFileAccountRepositoryTest {

    @TempDir
    Path tempDir;

    CsvFileAccountRepository sut;

    @BeforeEach
    void setUp() {
        sut = new CsvFileAccountRepository(tempDir.resolve("accounts.csv").toString());
    }

    // T5.1
    @Test
    void saveAll_writesHeaderAndOneLinePerAccount() throws Exception {
        var accounts = List.of(
                BankAccount.create("ING", "NL91ABNA0417164300", AccountType.CHECKING, new BigDecimal("1500.00"), "EUR"),
                BankAccount.create("Rabobank", "NL20INGB0001234567", AccountType.SAVINGS, new BigDecimal("250.75"), "EUR")
        );

        sut.saveAll(accounts);

        var lines = Files.readAllLines(tempDir.resolve("accounts.csv"));
        assertThat(lines.get(0)).isEqualTo("accountId,bankName,accountNumber,accountType,balance,currency,importedAt");
        assertThat(lines).hasSize(3); // header + 2 accounts
        assertThat(lines.get(1)).contains("ING").contains("NL91ABNA0417164300").contains("CHECKING");
        assertThat(lines.get(2)).contains("Rabobank").contains("NL20INGB0001234567").contains("SAVINGS");
    }

    // T5.2
    @Test
    void findAll_roundTrips_allFieldsIntact() {
        var original = BankAccount.create("ING", "NL91ABNA0417164300", AccountType.INVESTMENT, new BigDecimal("9999.99"), "USD");
        sut.saveAll(List.of(original));

        var loaded = sut.findAll();

        assertThat(loaded).hasSize(1);
        var loaded0 = loaded.get(0);
        assertThat(loaded0.accountId()).isEqualTo(original.accountId());
        assertThat(loaded0.bankName()).isEqualTo("ING");
        assertThat(loaded0.accountNumber()).isEqualTo("NL91ABNA0417164300");
        assertThat(loaded0.accountType()).isEqualTo(AccountType.INVESTMENT);
        assertThat(loaded0.balance()).isEqualByComparingTo("9999.99");
        assertThat(loaded0.currency()).isEqualTo("USD");
        assertThat(loaded0.importedAt()).isNotNull();
    }

    // T5.3
    @Test
    void findAll_whenFileDoesNotExist_returnsEmptyList() {
        var repo = new CsvFileAccountRepository(tempDir.resolve("nonexistent.csv").toString());

        assertThat(repo.findAll()).isEmpty();
    }

    // T5.4
    @Test
    void deleteAll_leavesOnlyHeaderRow() throws Exception {
        sut.saveAll(List.of(
                BankAccount.create("ING", "ACC1", AccountType.CHECKING, BigDecimal.ONE, "EUR")
        ));

        sut.deleteAll();

        var lines = Files.readAllLines(tempDir.resolve("accounts.csv"));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("accountId");
    }

    // T5.5
    @Test
    void saveAll_createsParentDirectoriesWhenMissing() {
        var nestedPath = tempDir.resolve("nested/deep/accounts.csv").toString();
        var repo = new CsvFileAccountRepository(nestedPath);

        repo.saveAll(List.of(
                BankAccount.create("ING", "ACC1", AccountType.CHECKING, BigDecimal.ONE, "EUR")
        ));

        assertThat(Files.exists(Path.of(nestedPath))).isTrue();
    }
}
