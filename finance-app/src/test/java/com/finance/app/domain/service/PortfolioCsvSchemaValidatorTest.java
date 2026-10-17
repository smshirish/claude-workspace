package com.finance.app.domain.service;

import com.finance.app.domain.exception.CsvSchemaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PortfolioCsvSchemaValidatorTest {

    private final PortfolioCsvSchemaValidator sut = new PortfolioCsvSchemaValidator();

    // S-1: all 10 IBKR columns in correct order → no exception
    @Test
    void validate_allTenColumnsCorrectOrder_noExceptionThrown() {
        String[] header = {
            "Account ID", "Symbol", "Asset Class", "Quantity", "Price",
            "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L"
        };

        assertThatNoException().isThrownBy(() -> sut.validate(header));
    }

    // S-2: header missing "Market Value" (9 columns) → CsvSchemaException
    @Test
    void validate_missingMarketValueColumn_throwsCsvSchemaException() {
        String[] header = {
            "Account ID", "Symbol", "Asset Class", "Quantity", "Price",
            "Currency", "Report Date", "Cost Basis", "Unrealized P&L"
        };

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }

    // S-3: "symbol" (lowercase) at position 1 instead of "Symbol" → CsvSchemaException mentioning expected/found
    @Test
    void validate_lowercaseSymbolAtPosition1_throwsCsvSchemaExceptionMentioningExpectedColumn() {
        String[] header = {
            "Account ID", "symbol", "Asset Class", "Quantity", "Price",
            "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L"
        };

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class)
                .satisfies(ex -> {
                    String msg = ((CsvSchemaException) ex).getSchemaError();
                    assertThat(msg).containsAnyOf("Symbol", "symbol", "position 2", "2");
                });
    }

    // S-4: 10 correct columns + 2 extra trailing → no exception (extra columns tolerated)
    @Test
    void validate_tenCorrectColumnsPlusTwoExtra_noExceptionThrown() {
        String[] header = {
            "Account ID", "Symbol", "Asset Class", "Quantity", "Price",
            "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L",
            "Extra1", "Extra2"
        };

        assertThatNoException().isThrownBy(() -> sut.validate(header));
    }

    // S-5: empty header array → CsvSchemaException mentioning 0 columns
    @Test
    void validate_emptyHeaderArray_throwsCsvSchemaException() {
        String[] header = {};

        assertThatThrownBy(() -> sut.validate(header))
                .isInstanceOf(CsvSchemaException.class);
    }
}
