package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.model.validation.RowValidationError;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.service.AccountCsvRowValidator;
import com.finance.app.domain.service.AccountCsvSchemaValidator;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OpenCsvAccountParser implements AccountFileParser {

    private final AccountCsvSchemaValidator schemaValidator = new AccountCsvSchemaValidator();
    private final AccountCsvRowValidator rowValidator = new AccountCsvRowValidator();

    @Override
    public List<BankAccount> parse(InputStream inputStream, String fileName) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new AccountImportException("Failed to read file: " + fileName, e);
        }

        // Pass 1: schema validation on header row only
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new AccountImportException("File is empty: " + fileName);
            }
            schemaValidator.validate(header);
        } catch (AccountImportException e) {
            throw e;
        } catch (Exception e) {
            throw new AccountImportException("Failed to parse file: " + fileName, e);
        }

        // Pass 2: collect all data rows and run row validation
        List<String[]> dataRows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            reader.readNext(); // skip header
            String[] row;
            while ((row = reader.readNext()) != null) {
                dataRows.add(row);
            }
        } catch (Exception e) {
            throw new AccountImportException("Failed to parse file: " + fileName, e);
        }

        List<RowValidationError> rowErrors = rowValidator.validate(dataRows);
        if (!rowErrors.isEmpty()) {
            throw new CsvRowValidationException(rowErrors);
        }

        // Mapping pass — only reached when both tiers pass
        List<BankAccount> accounts = new ArrayList<>();
        for (String[] row : dataRows) {
            AccountType accountType;
            try {
                accountType = AccountType.valueOf(row[2]);
            } catch (IllegalArgumentException e) {
                throw new AccountImportException("Unrecognised accountType: " + row[2], e);
            }
            accounts.add(BankAccount.create(
                    row[0],
                    row[1],
                    accountType,
                    new BigDecimal(row[3]),
                    row[4]
            ));
        }
        return accounts;
    }
}
