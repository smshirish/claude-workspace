package com.finance.app.application.service;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;

import java.util.List;
import java.util.Objects;

public class AccountApplicationService implements ImportAccountsUseCase, GetAllAccountsUseCase {

    private final AccountFileParser parser;
    private final AccountRepository repository;

    public AccountApplicationService(AccountFileParser parser, AccountRepository repository) {
        this.parser = Objects.requireNonNull(parser);
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<BankAccount> importAccounts(ImportAccountsCommand command) {
        var accounts = parser.parse(command.fileContent(), command.fileName());
        if (accounts.isEmpty()) {
            throw new AccountImportException("No accounts found in file: " + command.fileName());
        }
        repository.deleteAll();
        repository.saveAll(accounts);
        return accounts;
    }

    @Override
    public List<BankAccount> getAllAccounts() {
        return repository.findAll();
    }
}
