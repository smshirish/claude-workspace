package com.finance.app.domain.model;

public record PortfolioSortCriteria(PortfolioSortField field, SortDirection direction) {
    public static final PortfolioSortCriteria DEFAULT =
            new PortfolioSortCriteria(PortfolioSortField.SYMBOL, SortDirection.ASC);
}
