package com.finance.app.domain.port.in;

import java.io.InputStream;
import java.time.LocalDate;

public interface ImportPortfolioUseCase {

    record ImportPortfolioCommand(InputStream fileContent, String fileName) {}

    LocalDate importPortfolio(ImportPortfolioCommand command);
}
