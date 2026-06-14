package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.out.AccountFileParser;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenCsvAccountParser implements AccountFileParser {

    @Override
    public List<BankAccount> parse(InputStream inputStream, String fileName) {
        try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            List<AccountCsvRecord> records = new CsvToBeanBuilder<AccountCsvRecord>(reader)
                    .withType(AccountCsvRecord.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();

            return records.stream()
                    .map(this::toAccount)
                    .toList();
        } catch (AccountImportException e) {
            throw e;
        } catch (Exception e) {
            throw new AccountImportException("Failed to parse file: " + fileName, e);
        }
    }

    private BankAccount toAccount(AccountCsvRecord record) {
        if (record.getAccountNumber() == null || record.getAccountNumber().isBlank()) {
            throw new AccountImportException("Missing required column: accountNumber");
        }
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(record.getAccountType());
        } catch (IllegalArgumentException e) {
            throw new AccountImportException("Unrecognised accountType: " + record.getAccountType(), e);
        }
        return BankAccount.create(
                record.getBankName(),
                record.getAccountNumber(),
                accountType,
                new BigDecimal(record.getBalance()),
                record.getCurrency()
        );
    }
}
