package com.finance.app.application.service;

import com.finance.app.domain.exception.PortfolioImportException;
import com.finance.app.domain.model.PortfolioFilterCriteria;
import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.model.PortfolioSortCriteria;
import com.finance.app.domain.model.PortfolioSortField;
import com.finance.app.domain.model.SortDirection;
import com.finance.app.domain.port.in.FilterPortfolioUseCase;
import com.finance.app.domain.port.in.GetLatestPortfolioUseCase;
import com.finance.app.domain.port.in.ImportPortfolioUseCase;
import com.finance.app.domain.port.out.PortfolioFileParser;
import com.finance.app.domain.port.out.PortfolioRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PortfolioApplicationService
        implements ImportPortfolioUseCase, GetLatestPortfolioUseCase, FilterPortfolioUseCase {

    private final PortfolioFileParser parser;
    private final PortfolioRepository repository;

    public PortfolioApplicationService(PortfolioFileParser parser, PortfolioRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    @Override
    public LocalDate importPortfolio(ImportPortfolioCommand command) {
        List<PortfolioPosition> positions = parser.parse(command.fileContent(), command.fileName());
        if (positions.isEmpty()) {
            throw new PortfolioImportException("No positions found in the uploaded file");
        }
        LocalDate reportDate = positions.get(0).reportDate();
        repository.save(reportDate, positions);
        return reportDate;
    }

    @Override
    public Optional<PortfolioSnapshot> getLatestPositions(PortfolioSortCriteria criteria) {
        return repository.findLatest()
                .map(snapshot -> new PortfolioSnapshot(
                        snapshot.asOfDate(),
                        sortPositions(snapshot.positions(), criteria)));
    }

    @Override
    public Optional<PortfolioSnapshot> filterPositions(PortfolioFilterCriteria filterCriteria,
                                                        PortfolioSortCriteria sortCriteria) {
        return repository.findLatest()
                .map(snapshot -> {
                    List<PortfolioPosition> filtered = snapshot.positions().stream()
                            .filter(p -> filterCriteria.symbol().isBlank() ||
                                    p.symbol().toLowerCase().contains(filterCriteria.symbol().toLowerCase()))
                            .filter(p -> filterCriteria.assetClass().isBlank() ||
                                    p.assetClass().toLowerCase().contains(filterCriteria.assetClass().toLowerCase()))
                            .collect(Collectors.toList());
                    return new PortfolioSnapshot(snapshot.asOfDate(), sortPositions(filtered, sortCriteria));
                });
    }

    private List<PortfolioPosition> sortPositions(List<PortfolioPosition> positions,
                                                    PortfolioSortCriteria criteria) {
        Comparator<PortfolioPosition> comparator = switch (criteria.field()) {
            case SYMBOL      -> Comparator.comparing(PortfolioPosition::symbol);
            case ASSET_CLASS -> Comparator.comparing(PortfolioPosition::assetClass);
            case MARKET_VALUE -> Comparator.comparing(PortfolioPosition::marketValue);
            case UNREALIZED_PNL -> Comparator.comparing(
                    PortfolioPosition::unrealizedPnl,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (criteria.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return positions.stream().sorted(comparator).toList();
    }
}
