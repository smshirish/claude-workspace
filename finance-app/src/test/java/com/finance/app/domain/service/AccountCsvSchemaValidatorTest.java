package com.finance.app.domain.service;

import com.finance.app.domain.exception.CsvSchemaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AccountCsvSchemaValidatorTest {

    private final AccountCsvSchemaValidator sut = new AccountCsvSchemaValidator();

    // S-1 (update): correct 6-column header (including asOfDate) → no exception
    @Test
    void validate_correctSixColumnHeader_noExceptionThrown() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency", "asOfDate"};

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

    // S-5 (update): 7-column header — required 6 (including asOfDate) + extra trailing "notes" → no exception
    @Test
    void validate_extraTrailingColumn_noExceptionThrown() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency", "asOfDate", "notes"};

        assertThatNoException().isThrownBy(() -> sut.validate(header));
    }

    // S-6: empty header (zero columns) → CsvSchemaException
    @Test
    void validate_emptyHeader_throwsCsvSchemaException() {
        String[] header = {};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }

    // S-7 (new): 5-column header (first 5 correct, "asOfDate" absent) → CsvSchemaException
    @Test
    void validate_fiveColumnHeaderMissingAsOfDate_throwsCsvSchemaException() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency"};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }

    // S-8 (new): 6-column header with wrong name at position 6 → CsvSchemaException referencing asOfDate or position 6
    @Test
    void validate_sixColumnHeaderWrongNameAtPosition6_throwsCsvSchemaException() {
        String[] header = {"bankName", "accountNumber", "accountType", "balance", "currency", "wrongName"};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class)
                .satisfies(ex -> {
                    String schemaError = ((CsvSchemaException) ex).getSchemaError();
                    assertThat(schemaError).containsAnyOf("asOfDate", "position 6", "6");
                });
    }
}
