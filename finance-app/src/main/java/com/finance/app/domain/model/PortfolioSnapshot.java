package com.finance.app.domain.model;

import java.time.LocalDate;
import java.util.List;

public record PortfolioSnapshot(LocalDate asOfDate, List<PortfolioPosition> positions) {}
