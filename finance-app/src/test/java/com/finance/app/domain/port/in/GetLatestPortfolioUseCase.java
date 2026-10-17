package com.finance.app.domain.port.in;

import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.model.PortfolioSortCriteria;

import java.util.Optional;

public interface GetLatestPortfolioUseCase {

    Optional<PortfolioSnapshot> getLatestPositions(PortfolioSortCriteria criteria);
}
