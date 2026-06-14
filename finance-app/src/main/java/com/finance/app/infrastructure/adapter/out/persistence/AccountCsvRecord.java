package com.finance.app.infrastructure.adapter.out.persistence;

import com.opencsv.bean.CsvBindByName;

public class AccountCsvRecord {

    @CsvBindByName(column = "bankName")
    private String bankName;

    @CsvBindByName(column = "accountNumber")
    private String accountNumber;

    @CsvBindByName(column = "accountType")
    private String accountType;

    @CsvBindByName(column = "balance")
    private String balance;

    @CsvBindByName(column = "currency")
    private String currency;

    public String getBankName() { return bankName; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getBalance() { return balance; }
    public String getCurrency() { return currency; }
}
