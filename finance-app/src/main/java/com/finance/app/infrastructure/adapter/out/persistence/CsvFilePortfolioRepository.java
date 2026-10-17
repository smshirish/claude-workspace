package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.PortfolioImportException;
import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.port.out.PortfolioRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CsvFilePortfolioRepository implements PortfolioRepository {

    private static final String HEADER = "accountId,symbol,assetClass,quantity,price,marketValue,currency,reportDate,costBasis,unrealizedPnl";
    private static final Pattern DATE_CSV_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.csv");

    private final Path portfolioDir;

    public CsvFilePortfolioRepository(String portfolioDir) {
        this.portfolioDir = Path.of(portfolioDir);
    }

    @Override
    public void save(LocalDate asOfDate, List<PortfolioPosition> positions) {
        try {
            Files.createDirectories(portfolioDir);
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            for (PortfolioPosition p : positions) {
                lines.add(formatLine(p));
            }
            Files.write(portfolioDir.resolve(asOfDate + ".csv"), lines);
        } catch (IOException e) {
            throw new PortfolioImportException("Failed to write portfolio file for " + asOfDate, e);
        }
    }

    @Override
    public Optional<PortfolioSnapshot> findLatest() {
        if (!Files.exists(portfolioDir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(portfolioDir)) {
            List<Path> csvFiles = stream
                    .filter(p -> DATE_CSV_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
            if (csvFiles.isEmpty()) {
                return Optional.empty();
            }
            Path latestFile = csvFiles.get(csvFiles.size() - 1);
            LocalDate asOfDate = LocalDate.parse(
                    latestFile.getFileName().toString().replace(".csv", ""));
            List<PortfolioPosition> positions = readPositions(latestFile);
            return Optional.of(new PortfolioSnapshot(asOfDate, positions));
        } catch (IOException e) {
            throw new PortfolioImportException("Failed to read portfolio directory", e);
        }
    }

    private List<PortfolioPosition> readPositions(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> !line.startsWith("accountId") && !line.isBlank())
                    .map(this::parseLine)
                    .toList();
        } catch (IOException e) {
            throw new PortfolioImportException("Failed to read portfolio file: " + file, e);
        }
    }

    private PortfolioPosition parseLine(String line) {
        String[] p = line.split(",", -1);
        BigDecimal costBasis = (p.length > 8 && !p[8].isBlank()) ? new BigDecimal(p[8]) : null;
        BigDecimal unrealizedPnl = (p.length > 9 && !p[9].isBlank()) ? new BigDecimal(p[9]) : null;
        return PortfolioPosition.of(
                p[0], p[1], p[2],
                new BigDecimal(p[3]),
                new BigDecimal(p[4]),
                new BigDecimal(p[5]),
                p[6],
                LocalDate.parse(p[7]),
                costBasis,
                unrealizedPnl
        );
    }

    private String formatLine(PortfolioPosition p) {
        return String.join(",",
                p.accountId(),
                p.symbol(),
                p.assetClass(),
                p.quantity().toPlainString(),
                p.price().toPlainString(),
                p.marketValue().toPlainString(),
                p.currency(),
                p.reportDate().toString(),
                p.costBasis() != null ? p.costBasis().toPlainString() : "",
                p.unrealizedPnl() != null ? p.unrealizedPnl().toPlainString() : ""
        );
    }
}
