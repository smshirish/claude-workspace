package com.finance.app.domain.port.in;

import com.finance.app.domain.model.BankAccount;

import java.io.InputStream;
import java.util.List;

public interface ImportAccountsUseCase {

    List<BankAccount> importAccounts(ImportAccountsCommand command);

    record ImportAccountsCommand(InputStream fileContent, String fileName) {}
}
