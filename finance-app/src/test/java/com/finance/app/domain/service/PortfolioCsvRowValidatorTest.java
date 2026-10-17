package com.finance.app.domain.service;

import com.finance.app.domain.model.validation.RowValidationError;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PortfolioCsvRowValidatorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(LocalDate.of(2024, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final PortfolioCsvRowValidator sut = new PortfolioCsvRowValidator(FIXED_CLOCK);

    // R-1: 1 valid row, all 10 fields, Report Date = today → empty error list
    @Test
    void validate_singleValidRow_returnsEmptyList() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "AAPL", "STK", "10", "150.00", "1500.00",
            "USD", "2024-11-01", "140.00", "100.00"
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).isEmpty();
    }

    // R-2: blank Symbol (position 1) → RowValidationError for Symbol with "Field is required"
    @Test
    void validate_blankSymbol_returnsErrorForSymbol() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "", "STK", "10", "150.00", "1500.00",
            "USD", "2024-11-01", "140.00", "100.00"
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo("Symbol");
            assertThat(e.message()).containsIgnoringCase("required");
        });
    }

    // R-3: Quantity = "not-a-number" → RowValidationError for Quantity with "not a valid decimal"
    @Test
    void validate_invalidQuantity_returnsDecimalError() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "AAPL", "STK", "not-a-number", "150.00", "1500.00",
            "USD", "2024-11-01", "140.00", "100.00"
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo("Quantity");
            assertThat(e.message()).containsIgnoringCase("decimal");
        });
    }

    // R-4: Report Date = tomorrow → RowValidationError with "must not be in the future"
    @Test
    void validate_futurereportDate_returnsFutureError() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "AAPL", "STK", "10", "150.00", "1500.00",
            "USD", "2024-11-02", "140.00", "100.00"
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo("Report Date");
            assertThat(e.message()).containsIgnoringCase("future");
        });
    }

    // R-5: Report Date = "2024-13-01" (invalid format) → RowValidationError with "not a valid date"
    @Test
    void validate_invalidDateFormat_returnsDateFormatError() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "AAPL", "STK", "10", "150.00", "1500.00",
            "USD", "2024-13-01", "140.00", "100.00"
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(e -> {
            assertThat(e.column()).isEqualTo("Report Date");
            assertThat(e.message()).containsIgnoringCase("date");
        });
    }

    // R-6: blank Cost Basis and blank Unrealized P&L → no errors (nullable columns)
    @Test
    void validate_blankNullableColumns_noErrors() {
        List<String[]> rows = List.<String[]>of(new String[]{
            "U1234567", "AAPL", "STK", "10", "150.00", "1500.00",
            "USD", "2024-11-01", "", ""
        });

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).isEmpty();
    }

    // R-7: two rows with different Report Date values (both past) → RowValidationError for row 2
    @Test
    void validate_twoRowsWithDifferentReportDates_returnsErrorForRow2() {
        List<String[]> rows = List.of(
            new String[]{"U1234567", "AAPL", "STK", "10", "150.00", "1500.00", "USD", "2024-10-01", "", ""},
            new String[]{"U1234567", "MSFT", "STK", "5",  "300.00", "1500.00", "USD", "2024-10-15", "", ""}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).anySatisfy(e -> {
            assertThat(e.rowNumber()).isEqualTo(2);
            assertThat(e.message()).containsIgnoringCase("same Report Date");
        });
    }

    // R-8: two rows sharing the same Report Date → empty error list
    @Test
    void validate_twoRowsSameReportDate_returnsEmptyList() {
        List<String[]> rows = List.of(
            new String[]{"U1234567", "AAPL", "STK", "10", "150.00", "1500.00", "USD", "2024-10-01", "", ""},
            new String[]{"U1234567", "MSFT", "STK", "5",  "300.00", "1500.00", "USD", "2024-10-01", "", ""}
        );

        List<RowValidationError> errors = sut.validate(rows);

        assertThat(errors).isEmpty();
    }
}
