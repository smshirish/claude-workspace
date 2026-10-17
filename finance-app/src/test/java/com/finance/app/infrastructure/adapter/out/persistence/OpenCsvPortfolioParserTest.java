package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
import com.finance.app.domain.exception.PortfolioImportException;
import com.finance.app.domain.model.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Failing integration tests for Feature PortfolioView — OpenCsvPortfolioParser (plan §4).
 *
 * RUNTIME FAILURE EXPECTED until OpenCsvPortfolioParser is moved to src/main/java
 * and fully implemented.
 */
class OpenCsvPortfolioParserTest {

    private final OpenCsvPortfolioParser sut = new OpenCsvPortfolioParser();

    private static final String VALID_HEADER =
            "Account ID,Symbol,Asset Class,Quantity,Price,Market Value,Currency,Report Date,Cost Basis,Unrealized P&L";

    // P-1: valid 10-column CSV, 2 data rows → List size 2, fields mapped correctly
    @Test
    void parse_validCsvTwoRows_returnsCorrectlyMappedPositions() {
        var csv = VALID_HEADER + "\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,140.00,100.00\n" +
                "U1234567,MSFT,STK,5,300.00,1500.00,USD,2024-11-01,280.00,-20.00\n";

        var result = sut.parse(toStream(csv), "portfolio.csv");

        assertThat(result).hasSize(2);
        PortfolioPosition first = result.get(0);
        assertThat(first.symbol()).isEqualTo("AAPL");
        assertThat(first.assetClass()).isEqualTo("STK");
        assertThat(first.reportDate()).isEqualTo(LocalDate.of(2024, 11, 1));
        assertThat(first.costBasis()).isNotNull();
        assertThat(first.unrealizedPnl()).isNotNull();
    }

    // P-2: CSV with UTF-8 BOM prefix → parsed correctly
    @Test
    void parse_csvWithUtf8Bom_parsedCorrectly() {
        var csvWithBom = "﻿" + VALID_HEADER + "\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,140.00,100.00\n";

        var result = sut.parse(toStream(csvWithBom), "portfolio.csv");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("AAPL");
    }

    // P-3: header row only (no data rows) → throws PortfolioImportException "No positions found"
    @Test
    void parse_headerOnlyNoDataRows_throwsPortfolioImportException() {
        var csv = VALID_HEADER + "\n";

        assertThatThrownBy(() -> sut.parse(toStream(csv), "portfolio.csv"))
                .isInstanceOf(PortfolioImportException.class)
                .hasMessageContaining("No positions");
    }

    // P-4: "Asset Class" header is lowercased "asset class" → throws CsvSchemaException
    @Test
    void parse_lowercaseAssetClassHeader_throwsCsvSchemaException() {
        var csv = "Account ID,Symbol,asset class,Quantity,Price,Market Value,Currency,Report Date,Cost Basis,Unrealized P&L\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,140.00,100.00\n";

        assertThatThrownBy(() -> sut.parse(toStream(csv), "portfolio.csv"))
                .isInstanceOf(CsvSchemaException.class);
    }

    // P-5: Quantity = "abc" on row 2 → CsvRowValidationException with 1 error for Quantity
    @Test
    void parse_invalidQuantityOnRow2_throwsCsvRowValidationExceptionForQuantity() {
        var csv = VALID_HEADER + "\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,140.00,100.00\n" +
                "U1234567,MSFT,STK,abc,300.00,1500.00,USD,2024-11-01,280.00,-20.00\n";

        assertThatThrownBy(() -> sut.parse(toStream(csv), "portfolio.csv"))
                .isInstanceOf(CsvRowValidationException.class)
                .satisfies(ex -> {
                    var errors = ((CsvRowValidationException) ex).getRowErrors();
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).column()).isEqualTo("Quantity");
                });
    }

    // P-6: blank Cost Basis on row 1 → positions.get(0).costBasis() is null
    @Test
    void parse_blankCostBasisOnRow1_costBasisIsNull() {
        var csv = VALID_HEADER + "\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,,100.00\n";

        var result = sut.parse(toStream(csv), "portfolio.csv");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).costBasis()).isNull();
    }

    // P-7: two rows with different Report Date values → CsvRowValidationException
    @Test
    void parse_twoRowsDifferentReportDates_throwsCsvRowValidationException() {
        var csv = VALID_HEADER + "\n" +
                "U1234567,AAPL,STK,10,150.00,1500.00,USD,2024-10-01,140.00,100.00\n" +
                "U1234567,MSFT,STK,5,300.00,1500.00,USD,2024-10-15,280.00,-20.00\n";

        assertThatThrownBy(() -> sut.parse(toStream(csv), "portfolio.csv"))
                .isInstanceOf(CsvRowValidationException.class);
    }

    private ByteArrayInputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
