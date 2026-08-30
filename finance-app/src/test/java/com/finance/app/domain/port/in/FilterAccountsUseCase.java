package com.finance.app.domain.port.in;

import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.model.BankAccount;

import java.util.List;

/**
 * Stub — delete once production interface lands in src/main/java/com/finance/app/domain/port/in/
 */
public interface FilterAccountsUseCase {
    List<BankAccount> filterAccounts(AccountFilterCriteria criteria);
}
