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

    // T4.1
    @Test
    void parse_validCsv_returnsThreeBankAccounts() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR
                Rabobank,NL20INGB0001234567,SAVINGS,250.75,EUR
                ABN AMRO,NL02ABNA0123456789,INVESTMENT,9999.99,USD
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).bankName()).isEqualTo("ING");
        assertThat(result.get(0).accountNumber()).isEqualTo("NL91ABNA0417164300");
        assertThat(result.get(0).accountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.get(1).bankName()).isEqualTo("Rabobank");
        assertThat(result.get(2).bankName()).isEqualTo("ABN AMRO");
    }

    // T4.2
    @Test
    void parse_unknownAccountType_throwsAccountImportException() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency
                ING,NL91ABNA0417164300,UNKNOWN_TYPE,1500.00,EUR
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(AccountImportException.class)
                .hasMessageContaining("UNKNOWN_TYPE");
    }

    // T4.3
    @Test
    void parse_headerOnlyWithNoDataRows_returnsEmptyList() {
        var csv = "bankName,accountNumber,accountType,balance,currency\n";

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).isEmpty();
    }

    // T4.4
    @Test
    void parse_missingRequiredColumn_throwsAccountImportException() {
        var csv = """
                bankName,accountType,balance,currency
                ING,CHECKING,1500.00,EUR
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(AccountImportException.class);
    }

    // T4.5
    @Test
    void parse_balanceIsMappedToBigDecimalExactly() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency
                ING,NL91ABNA0417164300,CHECKING,12345.67,EUR
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result.get(0).balance()).isEqualByComparingTo(new BigDecimal("12345.67"));
    }

    // P-2: wrong column order → throws CsvSchemaException
    @Test
    void parse_wrongColumnOrder_throwsCsvSchemaException() {
        var csv = """
                accountNumber,bankName,accountType,balance,currency
                NL91ABNA0417164300,ING,CHECKING,1500.00,EUR
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvSchemaException.class);
    }

    // P-3: missing column → throws CsvSchemaException
    @Test
    void parse_missingColumn_throwsCsvSchemaException() {
        var csv = """
                bankName,accountNumber,accountType,balance
                ING,NL91ABNA0417164300,CHECKING,1500.00
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvSchemaException.class);
    }

    // P-4: extra trailing column → returns BankAccount list (no exception thrown)
    @Test
    void parse_extraTrailingColumn_returnsBankAccountList() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency,notes
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR,some-note
                Rabobank,NL20INGB0001234567,SAVINGS,250.75,EUR,another-note
                """;

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bankName()).isEqualTo("ING");
        assertThat(result.get(1).bankName()).isEqualTo("Rabobank");
    }

    // P-5: valid schema, invalid accountType on row 2 → throws CsvRowValidationException with row 2 entry
    @Test
    void parse_validSchemaInvalidAccountTypeOnRow2_throwsCsvRowValidationExceptionWithRow2Entry() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency
                ING,NL91ABNA0417164300,CHECKING,1500.00,EUR
                Rabobank,NL20INGB0001234567,MORTGAGE,250.75,EUR
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

    // P-6: valid schema, 3 rows each with one error → throws CsvRowValidationException with 3 entries
    @Test
    void parse_validSchemaThreeRowsEachWithOneError_throwsCsvRowValidationExceptionWith3Entries() {
        var csv = """
                bankName,accountNumber,accountType,balance,currency
                ING,NL91ABNA0417164300,CHECKING,not-a-number,EUR
                ,NL20INGB0001234567,SAVINGS,250.75,EUR
                ABN AMRO,NL02ABNA0123456789,MORTGAGE,9999.99,USD
                """;

        assertThatThrownBy(() -> sut.parse(toStream(csv), "test.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var rowErrors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(rowErrors).hasSize(3);
                });
    }

    // P-7: header-only file → returns empty list
    @Test
    void parse_headerOnlyFile_returnsEmptyList() {
        var csv = "bankName,accountNumber,accountType,balance,currency\n";

        var result = sut.parse(toStream(csv), "test.csv");

        assertThat(result).isEmpty();
    }

    private ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
