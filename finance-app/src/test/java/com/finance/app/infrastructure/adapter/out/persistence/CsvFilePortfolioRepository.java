package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.port.out.PortfolioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class CsvFilePortfolioRepository implements PortfolioRepository {

    public CsvFilePortfolioRepository(String portfolioDir) {}

    @Override
    public void save(LocalDate asOfDate, List<PortfolioPosition> positions) {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }

    @Override
    public Optional<PortfolioSnapshot> findLatest() {
        throw new UnsupportedOperationException("stub — not implemented yet");
    }
}
