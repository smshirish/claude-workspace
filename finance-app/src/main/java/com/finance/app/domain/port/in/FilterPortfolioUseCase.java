package com.finance.app.domain.port.in;

import com.finance.app.domain.model.PortfolioFilterCriteria;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.model.PortfolioSortCriteria;

import java.util.Optional;

public interface FilterPortfolioUseCase {

    Optional<PortfolioSnapshot> filterPositions(PortfolioFilterCriteria filterCriteria, PortfolioSortCriteria sortCriteria);
}
