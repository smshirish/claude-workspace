package com.finance.app.domain.service;

import com.finance.app.domain.exception.CsvSchemaException;

public class PortfolioCsvSchemaValidator {

    public static final String[] EXPECTED_COLUMNS = {
        "Account ID", "Symbol", "Asset Class", "Quantity", "Price",
        "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L"
    };

    public void validate(String[] header) {
        int checkLength = Math.min(header.length, EXPECTED_COLUMNS.length);
        for (int i = 0; i < checkLength; i++) {
            if (!EXPECTED_COLUMNS[i].equals(header[i])) {
                throw new CsvSchemaException(
                        "Expected column '" + EXPECTED_COLUMNS[i] + "' at position " + (i + 1) +
                        " but found '" + header[i] + "'.");
            }
        }
        if (header.length < EXPECTED_COLUMNS.length) {
            throw new CsvSchemaException(
                    "CSV header has " + header.length + " column(s) but requires at least " +
                    EXPECTED_COLUMNS.length + ". Missing columns starting from position " +
                    (header.length + 1) + ".");
        }
    }
}
