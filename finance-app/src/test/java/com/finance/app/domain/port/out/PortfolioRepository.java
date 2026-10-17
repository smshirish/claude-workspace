package com.finance.app.domain.port.out;

import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {

    void save(LocalDate asOfDate, List<PortfolioPosition> positions);

    Optional<PortfolioSnapshot> findLatest();
}
