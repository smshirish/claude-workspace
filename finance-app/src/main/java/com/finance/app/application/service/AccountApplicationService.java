package com.finance.app.application.service;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.AccountSortCriteria;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.model.SortDirection;
import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.port.in.FilterAccountsUseCase;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AccountApplicationService implements ImportAccountsUseCase, GetAllAccountsUseCase, FilterAccountsUseCase {

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
    public List<BankAccount> getAllAccounts(AccountSortCriteria criteria) {
        List<BankAccount> accounts = repository.findAll();
        Comparator<BankAccount> comparator = switch (criteria.field()) {
            case BANK_NAME    -> Comparator.comparing(BankAccount::bankName);
            case BALANCE      -> Comparator.comparing(BankAccount::balance);
            case ACCOUNT_TYPE -> Comparator.comparing(a -> a.accountType().name());
        };
        if (criteria.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return accounts.stream().sorted(comparator).toList();
    }

    @Override
    public List<BankAccount> filterAccounts(AccountFilterCriteria criteria) {
        return repository.findAll().stream()
                .filter(a -> matches(a.bankName(), criteria.bankName()))
                .filter(a -> matches(a.accountNumber(), criteria.accountNumber()))
                .filter(a -> matches(a.accountType().name(), criteria.accountType()))
                .toList();
    }

    private boolean matches(String value, String filter) {
        return filter == null || filter.isBlank() ||
                value.toLowerCase().startsWith(filter.toLowerCase());
    }
}
