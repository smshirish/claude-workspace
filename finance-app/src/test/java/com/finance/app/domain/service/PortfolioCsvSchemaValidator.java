package com.finance.app.domain.service;

public class PortfolioCsvSchemaValidator {

    public static final String[] EXPECTED_COLUMNS = {
        "Account ID", "Symbol", "Asset Class", "Quantity", "Price",
        "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L"
    };

    public void validate(String[] header) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }
}
