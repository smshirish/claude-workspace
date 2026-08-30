package com.finance.app.domain.port.in;

import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.model.BankAccount;

import java.util.List;

public interface FilterAccountsUseCase {
    List<BankAccount> filterAccounts(AccountFilterCriteria criteria);
}
