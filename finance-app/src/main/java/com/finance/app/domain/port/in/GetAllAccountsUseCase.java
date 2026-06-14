package com.finance.app.domain.port.in;

import com.finance.app.domain.model.BankAccount;

import java.util.List;

public interface GetAllAccountsUseCase {

    List<BankAccount> getAllAccounts();
}
