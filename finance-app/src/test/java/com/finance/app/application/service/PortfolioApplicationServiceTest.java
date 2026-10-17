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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Failing tests for Feature PortfolioView — PortfolioApplicationService (plan §4).
 *
 * RUNTIME FAILURE EXPECTED until PortfolioApplicationService is moved to src/main/java
 * and fully implemented.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioApplicationServiceTest {

    @Mock private PortfolioFileParser parser;
    @Mock private PortfolioRepository repository;

    private PortfolioApplicationService sut;

    private static final LocalDate DATE_2024_11_01 = LocalDate.of(2024, 11, 1);

    private PortfolioPosition aapl;
    private PortfolioPosition msft;
    private PortfolioPosition goog;

    @BeforeEach
    void setUp() {
        sut = new PortfolioApplicationService(parser, repository);
        aapl = PortfolioPosition.of("U1", "AAPL", "STK", new BigDecimal("10"), new BigDecimal("150.00"),
                new BigDecimal("1500.00"), "USD", DATE_2024_11_01, null, new BigDecimal("50.00"));
        msft = PortfolioPosition.of("U1", "MSFT", "STK", new BigDecimal("5"),  new BigDecimal("300.00"),
                new BigDecimal("1500.00"), "USD", DATE_2024_11_01, null, new BigDecimal("-20.00"));
        goog = PortfolioPosition.of("U1", "GOOG", "STK", new BigDecimal("2"),  new BigDecimal("100.00"),
                new BigDecimal("200.00"),  "USD", DATE_2024_11_01, null, null);
    }

    // A-1: getLatestPositions(DEFAULT) — repository returns 3 positions → sorted by symbol ASC
    @Test
    void getLatestPositions_defaultSort_returnsBySymbolAsc() {
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(msft, goog, aapl))));

        Optional<PortfolioSnapshot> result =
                ((GetLatestPortfolioUseCase) sut).getLatestPositions(PortfolioSortCriteria.DEFAULT);

        assertThat(result).isPresent();
        assertThat(result.get().positions()).extracting(PortfolioPosition::symbol)
                .containsExactly("AAPL", "GOOG", "MSFT");
    }

    // A-2: getLatestPositions(MARKET_VALUE DESC) → positions in descending market value order
    @Test
    void getLatestPositions_marketValueDesc_returnsHighestFirst() {
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(goog, aapl, msft))));
        var criteria = new PortfolioSortCriteria(PortfolioSortField.MARKET_VALUE, SortDirection.DESC);

        Optional<PortfolioSnapshot> result =
                ((GetLatestPortfolioUseCase) sut).getLatestPositions(criteria);

        assertThat(result).isPresent();
        List<BigDecimal> values = result.get().positions().stream()
                .map(PortfolioPosition::marketValue).toList();
        assertThat(values.get(0)).isGreaterThanOrEqualTo(values.get(1));
        assertThat(values.get(1)).isGreaterThanOrEqualTo(values.get(2));
    }

    // A-3: getLatestPositions(UNREALIZED_PNL ASC) — one null unrealizedPnl → null sorts last
    @Test
    void getLatestPositions_unrealizedPnlAscNullSortsLast() {
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(aapl, msft, goog))));
        var criteria = new PortfolioSortCriteria(PortfolioSortField.UNREALIZED_PNL, SortDirection.ASC);

        Optional<PortfolioSnapshot> result =
                ((GetLatestPortfolioUseCase) sut).getLatestPositions(criteria);

        assertThat(result).isPresent();
        List<PortfolioPosition> positions = result.get().positions();
        assertThat(positions.get(positions.size() - 1).unrealizedPnl()).isNull();
    }

    // A-4: getLatestPositions(DEFAULT) — repository returns empty → returns Optional.empty()
    @Test
    void getLatestPositions_noSnapshot_returnsEmpty() {
        given(repository.findLatest()).willReturn(Optional.empty());

        Optional<PortfolioSnapshot> result =
                ((GetLatestPortfolioUseCase) sut).getLatestPositions(PortfolioSortCriteria.DEFAULT);

        assertThat(result).isEmpty();
    }

    // A-5: filterPositions(symbol="AAPL", "") — snapshot has AAPL and MSFT → only AAPL returned
    @Test
    void filterPositions_bySymbol_returnsMatchingPositionsOnly() {
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(aapl, msft))));
        var filter = new PortfolioFilterCriteria("AAPL", "");

        Optional<PortfolioSnapshot> result =
                ((FilterPortfolioUseCase) sut).filterPositions(filter, PortfolioSortCriteria.DEFAULT);

        assertThat(result).isPresent();
        assertThat(result.get().positions()).hasSize(1)
                .first().extracting(PortfolioPosition::symbol).isEqualTo("AAPL");
    }

    // A-6: filterPositions("", assetClass="STK") — snapshot has STK and OPT → only STK returned
    @Test
    void filterPositions_byAssetClass_returnsMatchingPositionsOnly() {
        PortfolioPosition opt = PortfolioPosition.of("U1", "AAPL_OPT", "OPT",
                new BigDecimal("1"), new BigDecimal("5.00"), new BigDecimal("5.00"),
                "USD", DATE_2024_11_01, null, null);
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(aapl, opt))));
        var filter = new PortfolioFilterCriteria("", "STK");

        Optional<PortfolioSnapshot> result =
                ((FilterPortfolioUseCase) sut).filterPositions(filter, PortfolioSortCriteria.DEFAULT);

        assertThat(result).isPresent();
        assertThat(result.get().positions()).extracting(PortfolioPosition::assetClass)
                .containsOnly("STK");
    }

    // A-7: filterPositions(symbol="aapl", "") — case-insensitive match → AAPL returned
    @Test
    void filterPositions_lowercaseSymbol_isCaseInsensitive() {
        given(repository.findLatest()).willReturn(Optional.of(
                new PortfolioSnapshot(DATE_2024_11_01, List.of(aapl, msft))));
        var filter = new PortfolioFilterCriteria("aapl", "");

        Optional<PortfolioSnapshot> result =
                ((FilterPortfolioUseCase) sut).filterPositions(filter, PortfolioSortCriteria.DEFAULT);

        assertThat(result).isPresent();
        assertThat(result.get().positions()).hasSize(1)
                .first().extracting(PortfolioPosition::symbol).isEqualTo("AAPL");
    }

    // A-8: importPortfolio — parser returns 2 positions with reportDate=2024-11-01
    //      → repository.save(2024-11-01, positions) called; returns LocalDate.of(2024,11,1)
    @Test
    void importPortfolio_validFile_savesAndReturnsReportDate() {
        List<PortfolioPosition> parsed = List.of(aapl, msft);
        given(parser.parse(any(), any())).willReturn(parsed);

        var command = new ImportPortfolioUseCase.ImportPortfolioCommand(
                new ByteArrayInputStream(new byte[0]), "portfolio.csv");

        LocalDate result = ((ImportPortfolioUseCase) sut).importPortfolio(command);

        then(repository).should().save(DATE_2024_11_01, parsed);
        assertThat(result).isEqualTo(DATE_2024_11_01);
    }

    // A-9: importPortfolio — parser throws PortfolioImportException → exception propagated unchanged
    @Test
    void importPortfolio_parserThrowsException_propagatesUnchanged() {
        given(parser.parse(any(), any())).willThrow(new PortfolioImportException("parse failure"));
        var command = new ImportPortfolioUseCase.ImportPortfolioCommand(
                new ByteArrayInputStream(new byte[0]), "portfolio.csv");

        assertThatThrownBy(() -> ((ImportPortfolioUseCase) sut).importPortfolio(command))
                .isInstanceOf(PortfolioImportException.class)
                .hasMessage("parse failure");
    }

    // A-10: importPortfolio — parser returns empty list → throws PortfolioImportException "No positions found"
    @Test
    void importPortfolio_emptyParserResult_throwsPortfolioImportException() {
        given(parser.parse(any(), any())).willReturn(List.of());
        var command = new ImportPortfolioUseCase.ImportPortfolioCommand(
                new ByteArrayInputStream(new byte[0]), "portfolio.csv");

        assertThatThrownBy(() -> ((ImportPortfolioUseCase) sut).importPortfolio(command))
                .isInstanceOf(PortfolioImportException.class)
                .hasMessageContaining("No positions");
    }
}
