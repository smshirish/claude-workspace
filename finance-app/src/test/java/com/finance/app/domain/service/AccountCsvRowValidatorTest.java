package com.finance.app.domain.service;

import com.finance.app.domain.model.validation.RowValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AccountCsvRowValidatorTest {

    private final AccountCsvRowValidator sut = new AccountCsvRowValidator();

    // R-1: all valid rows → empty list returned
    @Test
    void validate_allValidRows_returnsEmptyList() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR"},
                new String[]{"Rabobank", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).isEmpty();
    }

    // R-2: row 2 blank bankName → one RowValidationError(rowNumber=2, column="bankName")
    @Test
    void validate_row2BlankBankName_returnsOneErrorForBankName() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR"},
                new String[]{"", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(2);
        assertThat(error.column()).isEqualTo("bankName");
    }

    // R-3: row 3 invalid accountType → RowValidationError(column="accountType") with allowed values in message
    @Test
    void validate_row3InvalidAccountType_returnsErrorWithAllowedValues() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR"},
                new String[]{"Rabobank", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR"},
                new String[]{"ABN AMRO", "NL02ABNA0123456789", "MORTGAGE", "9999.99", "USD"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(3);
        assertThat(error.column()).isEqualTo("accountType");
        // message must mention the bad value and allowed types
        assertThat(error.message())
                .containsAnyOf("CHECKING", "SAVINGS", "CREDIT", "INVESTMENT", "OTHER");
    }

    // R-4: row 1 non-numeric balance → RowValidationError(column="balance")
    @Test
    void validate_row1NonNumericBalance_returnsErrorForBalance() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "not-a-number", "EUR"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(1);
        assertThat(error.column()).isEqualTo("balance");
    }

    // R-5: multiple rows multiple errors → all errors collected
    @Test
    void validate_multipleRowsMultipleErrors_allErrorsCollected() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "not-a-number", "EUR"}, // row 1: bad balance
                new String[]{"", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR"},            // row 2: blank bankName
                new String[]{"ABN AMRO", "NL02ABNA0123456789", "MORTGAGE", "9999.99", "USD"}  // row 3: bad accountType
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(3);
        assertThat(errors).extracting(RowValidationError::rowNumber).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(errors).extracting(RowValidationError::column)
                .containsExactlyInAnyOrder("balance", "bankName", "accountType");
    }

    // R-6: blank accountNumber → RowValidationError(column="accountNumber")
    @Test
    void validate_blankAccountNumber_returnsErrorForAccountNumber() {
        List<String[]> rows = List.of(
                new String[]{"ING", "  ", "CHECKING", "1500.00", "EUR"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(1);
        assertThat(error.column()).isEqualTo("accountNumber");
    }
}
