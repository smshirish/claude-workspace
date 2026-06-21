package com.finance.app.domain.model;

// STUB — delete once src/main/java/.../domain/model/AccountSortCriteria.java is created by Feature A implementation
public record AccountSortCriteria(AccountSortField field, SortDirection direction) {
    public static final AccountSortCriteria DEFAULT =
            new AccountSortCriteria(AccountSortField.BALANCE, SortDirection.ASC);
}
