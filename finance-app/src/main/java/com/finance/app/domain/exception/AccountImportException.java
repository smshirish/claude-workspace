package com.finance.app.domain.exception;

public class AccountImportException extends RuntimeException {

    public AccountImportException(String message) {
        super(message);
    }

    public AccountImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
