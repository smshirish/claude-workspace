package com.finance.app.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PortfolioPosition {

    private final String accountId;
    private final String symbol;
    private final String assetClass;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal marketValue;
    private final String currency;
    private final LocalDate reportDate;
    private final BigDecimal costBasis;
    private final BigDecimal unrealizedPnl;

    private PortfolioPosition(String accountId, String symbol, String assetClass,
                               BigDecimal quantity, BigDecimal price, BigDecimal marketValue,
                               String currency, LocalDate reportDate,
                               BigDecimal costBasis, BigDecimal unrealizedPnl) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.assetClass = assetClass;
        this.quantity = quantity;
        this.price = price;
        this.marketValue = marketValue;
        this.currency = currency;
        this.reportDate = reportDate;
        this.costBasis = costBasis;
        this.unrealizedPnl = unrealizedPnl;
    }

    public static PortfolioPosition of(String accountId, String symbol, String assetClass,
                                        BigDecimal quantity, BigDecimal price, BigDecimal marketValue,
                                        String currency, LocalDate reportDate,
                                        BigDecimal costBasis, BigDecimal unrealizedPnl) {
        return new PortfolioPosition(accountId, symbol, assetClass, quantity, price,
                marketValue, currency, reportDate, costBasis, unrealizedPnl);
    }

    public PortfolioPosition withResolvedAccountId(String resolved) {
        return new PortfolioPosition(resolved, symbol, assetClass, quantity, price,
                marketValue, currency, reportDate, costBasis, unrealizedPnl);
    }

    public String accountId()      { return accountId; }
    public String symbol()         { return symbol; }
    public String assetClass()     { return assetClass; }
    public BigDecimal quantity()   { return quantity; }
    public BigDecimal price()      { return price; }
    public BigDecimal marketValue(){ return marketValue; }
    public String currency()       { return currency; }
    public LocalDate reportDate()  { return reportDate; }
    public BigDecimal costBasis()  { return costBasis; }
    public BigDecimal unrealizedPnl() { return unrealizedPnl; }
}
