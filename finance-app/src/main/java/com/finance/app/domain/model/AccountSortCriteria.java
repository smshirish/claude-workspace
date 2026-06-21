package com.finance.app.domain.model;

public record AccountSortCriteria(AccountSortField field, SortDirection direction) {
    public static final AccountSortCriteria DEFAULT =
            new AccountSortCriteria(AccountSortField.BALANCE, SortDirection.ASC);
}
