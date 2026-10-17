package com.finance.app.domain.service;

import com.finance.app.domain.model.validation.RowValidationError;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PortfolioCsvRowValidator {

    private static final String[] COLUMN_NAMES = PortfolioCsvSchemaValidator.EXPECTED_COLUMNS;
    private static final int[] DECIMAL_COLUMNS = {3, 4, 5}; // Quantity, Price, Market Value
    private static final int DATE_COLUMN = 7; // Report Date

    private final Clock clock;

    public PortfolioCsvRowValidator() {
        this.clock = Clock.systemDefaultZone();
    }

    public PortfolioCsvRowValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public List<RowValidationError> validate(List<String[]> rawRows) {
        List<RowValidationError> errors = new ArrayList<>();
        List<LocalDate> reportDates = new ArrayList<>();

        for (int i = 0; i < rawRows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rawRows.get(i);

            // Blank checks for required fields (columns 0-7)
            for (int col = 0; col < 8; col++) {
                String value = col < row.length ? row[col] : "";
                if (value == null || value.isBlank()) {
                    errors.add(new RowValidationError(rowNumber, COLUMN_NAMES[col], "Field is required"));
                }
            }

            // Decimal parse for Quantity (3), Price (4), Market Value (5)
            for (int col : DECIMAL_COLUMNS) {
                String value = col < row.length ? row[col] : "";
                if (value != null && !value.isBlank()) {
                    try {
                        new BigDecimal(value);
                    } catch (NumberFormatException e) {
                        errors.add(new RowValidationError(rowNumber, COLUMN_NAMES[col],
                                "'" + value + "' is not a valid decimal number"));
                    }
                }
            }

            // Date parse + future date check for Report Date (7)
            String dateValue = DATE_COLUMN < row.length ? row[DATE_COLUMN] : "";
            LocalDate parsedDate = null;
            if (dateValue != null && !dateValue.isBlank()) {
                try {
                    parsedDate = LocalDate.parse(dateValue);
                    if (parsedDate.isAfter(LocalDate.now(clock))) {
                        errors.add(new RowValidationError(rowNumber, COLUMN_NAMES[DATE_COLUMN],
                                "Date must not be in the future"));
                    }
                } catch (DateTimeParseException e) {
                    errors.add(new RowValidationError(rowNumber, COLUMN_NAMES[DATE_COLUMN],
                            "'" + dateValue + "' is not a valid date (expected YYYY-MM-DD)"));
                }
            }
            reportDates.add(parsedDate);
        }

        // Cross-row: all rows must share the same Report Date
        LocalDate firstValidDate = reportDates.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (firstValidDate != null) {
            for (int i = 0; i < reportDates.size(); i++) {
                LocalDate d = reportDates.get(i);
                if (d != null && !d.equals(firstValidDate)) {
                    errors.add(new RowValidationError(i + 1, COLUMN_NAMES[DATE_COLUMN],
                            "All rows must share the same Report Date"));
                }
            }
        }

        return errors;
    }
}
