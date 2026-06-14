package com.finance.app.domain.port.out;

import com.finance.app.domain.model.BankAccount;

import java.util.List;

public interface AccountRepository {

    void saveAll(List<BankAccount> accounts);

    List<BankAccount> findAll();

    void deleteAll();
}
