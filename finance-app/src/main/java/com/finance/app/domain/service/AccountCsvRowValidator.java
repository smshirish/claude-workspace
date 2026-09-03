package com.finance.app.domain.service;

import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.validation.RowValidationError;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AccountCsvRowValidator {

    private static final String[] COLUMN_NAMES = AccountCsvSchemaValidator.EXPECTED_COLUMNS;
    private static final String ALLOWED_ACCOUNT_TYPES = Arrays.stream(AccountType.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    private final Clock clock;

    public AccountCsvRowValidator() {
        this.clock = Clock.systemDefaultZone();
    }

    public AccountCsvRowValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public List<RowValidationError> validate(List<String[]> rawRows) {
        List<RowValidationError> errors = new ArrayList<>();

        for (int i = 0; i < rawRows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rawRows.get(i);

            // Mandatory blank checks for all 5 columns
            for (int col = 0; col < COLUMN_NAMES.length; col++) {
                String value = col < row.length ? row[col] : "";
                if (value == null || value.isBlank()) {
                    errors.add(new RowValidationError(rowNumber, COLUMN_NAMES[col], "Field is required"));
                }
            }

            // accountType enum check (position 2) — only if not blank
            String accountTypeValue = row.length > 2 ? row[2] : "";
            if (accountTypeValue != null && !accountTypeValue.isBlank()) {
                try {
                    AccountType.valueOf(accountTypeValue);
                } catch (IllegalArgumentException e) {
                    errors.add(new RowValidationError(rowNumber, "accountType",
                            "'" + accountTypeValue + "' is not a valid account type. Allowed values: " + ALLOWED_ACCOUNT_TYPES));
                }
            }

            // balance parseability check (position 3) — only if not blank
            String balanceValue = row.length > 3 ? row[3] : "";
            if (balanceValue != null && !balanceValue.isBlank()) {
                try {
                    new BigDecimal(balanceValue);
                } catch (NumberFormatException e) {
                    errors.add(new RowValidationError(rowNumber, "balance",
                            "'" + balanceValue + "' is not a valid decimal number"));
                }
            }

            // asOfDate validation (position 5) — only if not blank (blank already caught above)
            String asOfDateValue = row.length > 5 ? row[5] : "";
            if (asOfDateValue != null && !asOfDateValue.isBlank()) {
                try {
                    LocalDate parsed = LocalDate.parse(asOfDateValue);
                    if (parsed.isAfter(LocalDate.now(clock))) {
                        errors.add(new RowValidationError(rowNumber, "asOfDate",
                                "Date must not be in the future"));
                    }
                } catch (DateTimeParseException e) {
                    errors.add(new RowValidationError(rowNumber, "asOfDate",
                            "'" + asOfDateValue + "' is not a valid date (expected YYYY-MM-DD)"));
                }
            }
        }

        return errors;
    }
}
