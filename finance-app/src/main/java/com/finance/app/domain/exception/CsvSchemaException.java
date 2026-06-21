package com.finance.app.domain.exception;

public class CsvSchemaException extends AccountImportException {

    private final String schemaError;

    public CsvSchemaException(String schemaError) {
        super(schemaError);
        this.schemaError = schemaError;
    }

    public String getSchemaError() {
        return schemaError;
    }
}
