package com.finance.app.domain.exception;

public class PortfolioImportException extends RuntimeException {

    public PortfolioImportException(String message) {
        super(message);
    }

    public PortfolioImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
