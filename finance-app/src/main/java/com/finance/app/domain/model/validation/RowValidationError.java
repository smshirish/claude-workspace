package com.finance.app.domain.model.validation;

public record RowValidationError(int rowNumber, String column, String message) {
}
