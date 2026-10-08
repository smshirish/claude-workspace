package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.exception.CsvSchemaException;
import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.model.AccountType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class OpenCsvAccountParserTest {

    private final OpenCsvAccountParser sut = new OpenCsvAccountParser();

    // T4.1 (update): add asOfDate column — valid CSV returns three BankAccounts with correct asOfDate
    @Test
    void parse_validCsv_returnsThreeBankAccountsWithCorrectAsOfDate() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,2026-01-15
                Rabobank,NL20INGB0001234567,SAVINGS,250.75,EUR,2026-08-31
                ABN AMRO,NL02ABNA0123456789,INVESTMENT,9999.99,USD,2020-06-01
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).bankName()).isEqualTo("ING");
        assertThat(result.get(0).accountNumber()).isEqualTo("NL91ABNA0417164300");
        assertThat(result.get(0).accountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.get(0).asOfDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 15));
        assertThat(result.get(1).bankName()).isEqualTo("Rabobank");
        assertThat(result.get(1).asOfDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 31));
        assertThat(result.get(2).bankName()).isEqualTo("ABN AMRO");
        assertThat(result.get(2).asOfDate()).isEqualTo(java.time.LocalDate.of(2020, 6, 1));
    }

    // T4.2 (update): add asOfDate column — unknown accountType throws AccountImportException
    @Test
    void parse_unknownAccountType_throwsAccountImportException() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,UNKNOWN_TYPE,1500.00,EUR,2026-01-15
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(AccountImportException.class)
                .hasMessageContaining("UNKNOWN_TYPE");
    }

    // T4.3 (update): add asOfDate column — header-only returns empty list
    @Test
    void parse_headerOnlyWithNoDataRows_returnsEmptyList() {
        var csv = "bankName,accountNumber,accountType,balance,currency,asOfDate\n";

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).isEmpty();
    }

    // T4.4 (update): missing accountNumber — still throws AccountImportException
    @Test
    void parse_missingRequiredColumn_throwsAccountImportException() {
        var csv = """
                bankName,accountType,balance,currency,asOfDate
                ING,CHECKING,1500.00,EUR,2026-01-15
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(AccountImportException.class);
    }

    // T4.5 (update): add asOfDate column — balance is mapped to BigDecimal exactly
    @Test
    void parse_balanceIsMappedToBigDecimalExactly() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,12345.67,EUR,2026-01-15
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result.get(0).balance()).isEqualByComparingTo(new BigDecimal("12345.67"));
    }

    // P-2 (update): add asOfDate column — wrong column order still throws CsvSchemaException
    @Test
    void parse_wrongColumnOrder_throwsCsvSchemaException() {
        var csv = """
                accountNumber,bankName,accountType,balance,currency,asOfDate
                NL91ABNA0417164300,ING,CHECKING,1500.00,EUR,2026-01-15
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvSchemaException.class);
    }

    // P-3 (update): missing column — still throws CsvSchemaException
    @Test
    void parse_missingColumn_throwsCsvSchemaException() {
        var csv = """
                bankName,accountNumber,accountType,balance
                ING,NL91ABNA0417164300,CHECKING,1500.00
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvSchemaException.class);
    }

    // P-4 (update): asOfDate at position 6, extra "notes" at position 7 — returns BankAccount list
    @Test
    void parse_extraTrailingColumn_returnsBankAccountList() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate,notes
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,2026-01-15,some-note
                Rabobank,NL20INGB0001234567,SAVINGS,250.75,EUR,2026-01-15,another-note
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bankName()).isEqualTo("ING");
        assertThat(result.get(1).bankName()).isEqualTo("Rabobank");
    }

    // P-5 (update): add asOfDate column — invalid accountType on row 2 throws CsvRowValidationException
    @Test
    void parse_validSchemaInvalidAccountTypeOnRow2_throwsCsvRowValidationExceptionWithRow2Entry() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,2026-01-15
                Rabobank,NL20INGB0001234567,MORTGAGE,250.75,EUR,2026-01-15
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).hasSize(1);
                    assertThat(rowErrors.get(0).rowNumber()).isEqualTo(2);
                    assertThat(rowErrors.get(0).column()).isEqualTo("accountType");
                });
    }

    // P-6 (update): add asOfDate column — three rows each with one error throws CsvRowValidationException with 3 entries
    @Test
    void parse_validSchemaThreeRowsEachWithOneError_throwsCsvRowValidationExceptionWith3Entries() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,not-a-number,EUR,2026-01-15
                ,NL20INGB0001234567,SAVINGS,250.75,EUR,2026-01-15
                ABN AMRO,NL02ABNA0123456789,MORTGAGE,9999.99,USD,2026-01-15
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).hasSize(3);
                });
    }

    // P-7 (update): add asOfDate column — header-only file returns empty list
    @Test
    void parse_headerOnlyFile_returnsEmptyList() {
        var csv = "bankName,accountNumber,accountType,balance,currency,asOfDate\n";

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).isEmpty();
    }

    // P-9 (new): valid schema; row 1 has blank asOfDate → CsvRowValidationException with asOfDate error on row 1
    @Test
    void parse_blankAsOfDateOnRow1_throwsCsvRowValidationExceptionForAsOfDate() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).anySatisfy(error -> {
                        assertThat(error.rowNumber()).isEqualTo(1);
                        assertThat(error.column()).isEqualTo("asOfDate");
                    });
                });
    }

    // P-10 (new): valid schema; row 1 asOfDate is a future date → CsvRowValidationException with asOfDate error on row 1
    @Test
    void parse_futureAsOfDateOnRow1_throwsCsvRowValidationExceptionForAsOfDate() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,2099-01-01
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).anySatisfy(error -> {
                        assertThat(error.rowNumber()).isEqualTo(1);
                        assertThat(error.column()).isEqualTo("asOfDate");
                    });
                });
    }

    // P-11 (new): valid schema; row 1 asOfDate = "31/08/2026" (non-ISO) → CsvRowValidationException with asOfDate error
    @Test
    void parse_nonIsoAsOfDateOnRow1_throwsCsvRowValidationExceptionForAsOfDate() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,asOfDate
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,31/08/2026
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).anySatisfy(error -> {
                        assertThat(error.rowNumber()).isEqualTo(1);
                        assertThat(error.column()).isEqualTo("asOfDate");
                    });
                });
    }

    private ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
