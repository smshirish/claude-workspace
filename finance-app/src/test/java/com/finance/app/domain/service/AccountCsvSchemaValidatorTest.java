package com.finance.app.domain.service;

import com.finance.app.domain.exception.CsvSchemaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AccountCsvSchemaValidatorTest {

    private final AccountCsvSchemaValidator sut = new AccountCsvSchemaValidator();

    // S-1: correct 5-column header in exact order → no exception
    @Test
    void validate_correctFiveColumnHeader_noExceptionThrown() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency"};

        assertThatNoException().isThrownBy(() -> sut.validate(header));
    }

    // S-2: column name typo → CsvSchemaException with message naming bad column/position
    @Test
    void validate_columnNameTypo_throwsCsvSchemaException() {
        String[] header = {"bankName", "acctNumber", "accountType", "balance", "currency"};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class)
                .satisfies(ex -> {
                    String schemaError = ((CsvSchemaException) ex).getSchemaError();
                    assertThat(schemaError).isNotBlank();
                    // message should reference the expected column name and/or position
                    assertThat(schemaError).containsAnyOf("accountNumber", "position 2", "2");
                });
    }

    // S-3: correct columns but wrong order → CsvSchemaException naming position mismatch
    @Test
    void validate_correctColumnsWrongOrder_throwsCsvSchemaException() {
        String[] header = {"accountNumber", "bankName", "accountType", "balance", "currency"};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class)
                .satisfies(ex -> {
                    String schemaError = ((CsvSchemaException) ex).getSchemaError();
                    assertThat(schemaError).isNotBlank();
                });
    }

    // S-4: missing column (only 4 columns) → CsvSchemaException
    @Test
    void validate_missingOneColumn_throwsCsvSchemaException() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance"};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }

    // S-5: extra column appended at end → no exception (extra column silently ignored)
    @Test
    void validate_extraTrailingColumn_noExceptionThrown() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency", "notes"};

        assertThatNoException().isThrownBy(() -> sut.validate(header));
    }

    // S-6: empty header (zero columns) → CsvSchemaException
    @Test
    void validate_emptyHeader_throwsCsvSchemaException() {
        String[] header = {};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }
}
