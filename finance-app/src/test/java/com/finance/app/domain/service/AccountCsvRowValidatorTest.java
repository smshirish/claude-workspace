package com.finance.app.domain.service;

import com.finance.app.domain.model.validation.RowValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AccountCsvRowValidatorTest {

    private final AccountCsvRowValidator sut = new AccountCsvRowValidator();

    // R-1 (update): all valid rows with asOfDate → empty list returned
    @Test
    void validate_allValidRows_returnsEmptyList() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2026-08-31"},
                new String[]{"Rabobank", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).isEmpty();
    }

    // R-2 (update): row 2 blank bankName → one RowValidationError(rowNumber=2, column="bankName")
    @Test
    void validate_row2BlankBankName_returnsOneErrorForBankName() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2026-08-31"},
                new String[]{"", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(2);
        assertThat(error.column()).isEqualTo("bankName");
    }

    // R-3 (update): row 3 invalid accountType → RowValidationError(column="accountType") with allowed values
    @Test
    void validate_row3InvalidAccountType_returnsErrorWithAllowedValues() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2026-08-31"},
                new String[]{"Rabobank", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR", "2026-08-31"},
                new String[]{"ABN AMRO", "NL02ABNA0123456789", "MORTGAGE", "9999.99", "USD", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(3);
        assertThat(error.column()).isEqualTo("accountType");
        assertThat(error.message())
                .containsAnyOf("CHECKING", "SAVINGS", "CREDIT", "INVESTMENT", "OTHER");
    }

    // R-4 (update): row 1 non-numeric balance → RowValidationError(column="balance")
    @Test
    void validate_row1NonNumericBalance_returnsErrorForBalance() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "not-a-number", "EUR", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(1);
        assertThat(error.column()).isEqualTo("balance");
    }

    // R-5 (update): multiple rows multiple errors → all errors collected
    @Test
    void validate_multipleRowsMultipleErrors_allErrorsCollected() {
        List<String[]> rows = List.of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "not-a-number", "EUR", "2026-08-31"},
                new String[]{"", "NL20INGB0001234567", "SAVINGS", "250.75", "EUR", "2026-08-31"},
                new String[]{"ABN AMRO", "NL02ABNA0123456789", "MORTGAGE", "9999.99", "USD", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(3);
        assertThat(errors).extracting(RowValidationError::rowNumber).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(errors).extracting(RowValidationError::column)
                .containsExactlyInAnyOrder("balance", "bankName", "accountType");
    }

    // R-6 (update): blank accountNumber → RowValidationError(column="accountNumber")
    @Test
    void validate_blankAccountNumber_returnsErrorForAccountNumber() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "  ", "CHECKING", "1500.00", "EUR", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).hasSize(1);
        RowValidationError error = errors.get(0);
        assertThat(error.rowNumber()).isEqualTo(1);
        assertThat(error.column()).isEqualTo("accountNumber");
    }

    // R-7 (new): blank asOfDate → RowValidationError(column="asOfDate", "Field is required")
    @Test
    void validate_blankAsOfDate_returnsErrorForAsOfDate() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", ""}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.column()).isEqualTo("asOfDate");
        });
    }

    // R-8 (new): non-ISO asOfDate "31/08/2026" → RowValidationError(column="asOfDate") with bad value in message
    @Test
    void validate_nonIsoAsOfDate_returnsErrorWithBadValueInMessage() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "31/08/2026"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.column()).isEqualTo("asOfDate");
            assertThat(error.message()).contains("31/08/2026");
        });
    }

    // R-9 (new): future asOfDate → RowValidationError(column="asOfDate")
    @Test
    void validate_futureAsOfDate_returnsErrorForAsOfDate() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2099-01-01"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.column()).isEqualTo("asOfDate");
        });
    }

    // R-10 (new): asOfDate = "2026-08-31" (today per fixed clock) → no error for asOfDate
    @Test
    void validate_todayAsOfDate_noErrorForAsOfDate() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2026-08-31"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).noneMatch(e -> "asOfDate".equals(e.column()));
    }

    // R-11 (new): asOfDate = "2020-01-01" (past date) → no error for asOfDate
    @Test
    void validate_pastAsOfDate_noErrorForAsOfDate() {
        List<String[]> rows = List.<String[]>of(
                new String[]{"ING", "NL91ABNA0417164300", "CHECKING", "1500.00", "EUR", "2020-01-01"}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).noneMatch(e -> "asOfDate".equals(e.column()));
    }
}
