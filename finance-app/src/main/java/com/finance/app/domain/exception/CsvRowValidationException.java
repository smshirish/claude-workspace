package com.finance.app.domain.exception;

import com.finance.app.domain.model.validation.RowValidationError;

import java.util.List;

public class CsvRowValidationException extends AccountImportException {

    private final List<RowValidationError> rowErrors;

    public CsvRowValidationException(List<RowValidationError> rowErrors) {
        super(buildMessage(rowErrors));
        this.rowErrors = List.copyOf(rowErrors);
    }

    private static String buildMessage(List<RowValidationError> rowErrors) {
        StringBuilder sb = new StringBuilder("CSV row validation failed with ")
                .append(rowErrors.size()).append(" error(s):");
        for (RowValidationError e : rowErrors) {
            sb.append(" [Row ").append(e.rowNumber()).append(", '").append(e.column())
              .append("': ").append(e.message()).append("]");
        }
        return sb.toString();
    }

    public List<RowValidationError> getRowErrors() {
        return rowErrors;
    }
}
