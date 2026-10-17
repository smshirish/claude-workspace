package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Failing integration tests for Feature PortfolioView — CsvFilePortfolioRepository (plan §4).
 *
 * RUNTIME FAILURE EXPECTED until CsvFilePortfolioRepository is moved to src/main/java
 * and fully implemented.
 */
class CsvFilePortfolioRepositoryTest {

    @TempDir
    Path tempDir;

    CsvFilePortfolioRepository sut;

    private static final LocalDate DATE_2024_11_01 = LocalDate.of(2024, 11, 1);
    private static final LocalDate DATE_2024_11_15 = LocalDate.of(2024, 11, 15);

    private static PortfolioPosition position(String symbol, BigDecimal marketValue, LocalDate date) {
        return PortfolioPosition.of("U1", symbol, "STK",
                BigDecimal.ONE, marketValue, marketValue, "USD", date, null, null);
    }

    @BeforeEach
    void setUp() {
        sut = new CsvFilePortfolioRepository(tempDir.resolve("portfolio").toString());
    }

    // P-8: save(2024-11-01, positions) — directory absent → directory and file created
    @Test
    void save_directoryAbsent_createsDirectoryAndFile() throws Exception {
        List<PortfolioPosition> positions = List.of(position("AAPL", new BigDecimal("1500.00"), DATE_2024_11_01));

        sut.save(DATE_2024_11_01, positions);

        Path expectedFile = tempDir.resolve("portfolio/2024-11-01.csv");
        assertThat(Files.exists(expectedFile)).isTrue();
        List<String> lines = Files.readAllLines(expectedFile);
        assertThat(lines.get(0)).contains("symbol");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("AAPL");
    }

    // P-9: findLatest() — directory has 2024-11-01.csv and 2024-11-15.csv → asOfDate = 2024-11-15
    @Test
    void findLatest_twoFiles_returnsLatestByDate() throws Exception {
        Path portfolioDir = tempDir.resolve("portfolio");
        Files.createDirectories(portfolioDir);
        String header = "accountId,symbol,assetClass,quantity,price,marketValue,currency,reportDate,costBasis,unrealizedPnl\n";
        Files.writeString(portfolioDir.resolve("2024-11-01.csv"), header + "U1,AAPL,STK,10,150.00,1500.00,USD,2024-11-01,,\n");
        Files.writeString(portfolioDir.resolve("2024-11-15.csv"), header + "U1,MSFT,STK,5,300.00,1500.00,USD,2024-11-15,,\n");

        Optional<PortfolioSnapshot> result = sut.findLatest();

        assertThat(result).isPresent();
        assertThat(result.get().asOfDate()).isEqualTo(DATE_2024_11_15);
        assertThat(result.get().positions()).hasSize(1);
        assertThat(result.get().positions().get(0).symbol()).isEqualTo("MSFT");
    }

    // P-10: findLatest() — portfolio directory is absent → Optional.empty()
    @Test
    void findLatest_directoryAbsent_returnsEmpty() {
        var repo = new CsvFilePortfolioRepository(tempDir.resolve("nonexistent").toString());

        Optional<PortfolioSnapshot> result = repo.findLatest();

        assertThat(result).isEmpty();
    }

    // P-11: findLatest() — directory exists but no .csv files → Optional.empty()
    @Test
    void findLatest_directoryExistsNoCsvFiles_returnsEmpty() throws Exception {
        Path portfolioDir = tempDir.resolve("portfolio");
        Files.createDirectories(portfolioDir);
        Files.writeString(portfolioDir.resolve("readme.txt"), "not a csv");

        Optional<PortfolioSnapshot> result = sut.findLatest();

        assertThat(result).isEmpty();
    }

    // P-12: save(2024-11-01, newPositions) when 2024-11-01.csv already exists → file overwritten
    @Test
    void save_existingFile_overwritesWithNewPositions() throws Exception {
        List<PortfolioPosition> original = List.of(position("AAPL", new BigDecimal("1500.00"), DATE_2024_11_01));
        sut.save(DATE_2024_11_01, original);

        List<PortfolioPosition> replacement = List.of(position("MSFT", new BigDecimal("1500.00"), DATE_2024_11_01));
        sut.save(DATE_2024_11_01, replacement);

        Path file = tempDir.resolve("portfolio/2024-11-01.csv");
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("MSFT");
        assertThat(lines.get(1)).doesNotContain("AAPL");
    }
}
