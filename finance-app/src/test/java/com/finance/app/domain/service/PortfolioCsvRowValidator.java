package com.finance.app.domain.service;

import com.finance.app.domain.model.validation.RowValidationError;

import java.time.Clock;
import java.util.List;

public class PortfolioCsvRowValidator {

    public PortfolioCsvRowValidator() {}

    public PortfolioCsvRowValidator(Clock clock) {}

    public List<RowValidationError> validate(List<String[]> rawRows) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }
}
