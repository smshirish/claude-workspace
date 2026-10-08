package com.finance.app.domain.service;

import com.finance.app.domain.exception.CsvSchemaException;

public class AccountCsvSchemaValidator {

    static final String[] EXPECTED_COLUMNS = {"bankName", "accountNumber", "accountType", "balance", "currency", "asOfDate"};

    public void validate(String[] actualHeader) {
        int checkLength = Math.min(actualHeader.length, EXPECTED_COLUMNS.length);
        for (int i = 0; i < checkLength; i++) {
            if (!EXPECTED_COLUMNS[i].equals(actualHeader[i])) {
                throw new CsvSchemaException(
                        "Expected column '" + EXPECTED_COLUMNS[i] + "' at position " + (i + 1) +
                        " but found '" + actualHeader[i] + "'."
                );
            }
        }
        if (actualHeader.length < EXPECTED_COLUMNS.length) {
            throw new CsvSchemaException(
                    "CSV header has " + actualHeader.length + " column(s) but requires at least " +
                    EXPECTED_COLUMNS.length + ". Missing columns starting from position " +
                    (actualHeader.length + 1) + "."
            );
        }
    }
}
