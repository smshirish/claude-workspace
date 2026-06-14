package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.AccountId;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.out.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CsvFileAccountRepository implements AccountRepository {

    private static final String HEADER = "accountId,bankName,accountNumber,accountType,balance,currency,importedAt";

    private final Path filePath;

    public CsvFileAccountRepository(@Value("${finance.storage.accounts-file}") String filePath) {
        this.filePath = Path.of(filePath);
    }

    @Override
    public void saveAll(List<BankAccount> accounts) {
        try {
            Files.createDirectories(filePath.getParent());
            var lines = new ArrayList<String>();
            lines.add(HEADER);
            accounts.stream().map(this::formatLine).forEach(lines::add);
            Files.write(filePath, lines);
        } catch (IOException e) {
            throw new AccountImportException("Failed to write accounts file: " + filePath, e);
        }
    }

    @Override
    public List<BankAccount> findAll() {
        if (!Files.exists(filePath)) return List.of();
        try {
            return Files.readAllLines(filePath).stream()
                    .filter(line -> !line.startsWith("accountId") && !line.isBlank())
                    .map(this::parseLine)
                    .toList();
        } catch (IOException e) {
            throw new AccountImportException("Failed to read accounts file: " + filePath, e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            if (!Files.exists(filePath)) return;
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, List.of(HEADER));
        } catch (IOException e) {
            throw new AccountImportException("Failed to delete accounts in file: " + filePath, e);
        }
    }

    private BankAccount parseLine(String line) {
        var p = line.split(",", 7);
        return new BankAccount(
                AccountId.of(p[0]),
                p[1],
                p[2],
                AccountType.valueOf(p[3]),
                new BigDecimal(p[4]),
                p[5],
                LocalDateTime.parse(p[6])
        );
    }

    private String formatLine(BankAccount a) {
        return String.join(",",
                a.accountId().toString(),
                a.bankName(),
                a.accountNumber(),
                a.accountType().name(),
                a.balance().toPlainString(),
                a.currency(),
                a.importedAt().toString()
        );
    }
}
