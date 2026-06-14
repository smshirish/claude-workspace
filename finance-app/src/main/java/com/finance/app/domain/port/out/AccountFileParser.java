package com.finance.app.domain.port.out;

import com.finance.app.domain.model.BankAccount;

import java.io.InputStream;
import java.util.List;

public interface AccountFileParser {

    List<BankAccount> parse(InputStream inputStream, String fileName);
}
