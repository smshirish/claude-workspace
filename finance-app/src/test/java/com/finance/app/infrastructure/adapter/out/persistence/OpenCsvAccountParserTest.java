package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.AccountImportException;
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

    private ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
