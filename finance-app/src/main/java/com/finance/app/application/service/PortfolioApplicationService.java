package com.finance.app.application.service;

import com.finance.app.domain.model.PortfolioFilterCriteria;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.model.PortfolioSortCriteria;
import com.finance.app.domain.port.in.FilterPortfolioUseCase;
import com.finance.app.domain.port.in.GetLatestPortfolioUseCase;
import com.finance.app.domain.port.in.ImportPortfolioUseCase;
import com.finance.app.domain.port.out.PortfolioFileParser;
import com.finance.app.domain.port.out.PortfolioRepository;

import java.time.LocalDate;
import java.util.Optional;

public class PortfolioApplicationService
        implements ImportPortfolioUseCase, GetLatestPortfolioUseCase, FilterPortfolioUseCase {

    public PortfolioApplicationService(PortfolioFileParser parser, PortfolioRepository repository) {}

    @Override
    public LocalDate importPortfolio(ImportPortfolioCommand command) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }

    @Override
    public Optional<PortfolioSnapshot> getLatestPositions(PortfolioSortCriteria criteria) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }

    @Override
    public Optional<PortfolioSnapshot> filterPositions(PortfolioFilterCriteria filterCriteria,
                                                        PortfolioSortCriteria sortCriteria) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }
}
