package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
import com.finance.app.domain.exception.PortfolioImportException;
import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.validation.RowValidationError;
import com.finance.app.domain.port.out.PortfolioFileParser;
import com.finance.app.domain.service.PortfolioCsvRowValidator;
import com.finance.app.domain.service.PortfolioCsvSchemaValidator;
import com.opencsv.CSVReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OpenCsvPortfolioParser implements PortfolioFileParser {

    private final PortfolioCsvSchemaValidator schemaValidator = new PortfolioCsvSchemaValidator();
    private final PortfolioCsvRowValidator rowValidator;

    public OpenCsvPortfolioParser() {
        this(Clock.systemDefaultZone());
    }

    public OpenCsvPortfolioParser(Clock clock) {
        this.rowValidator = new PortfolioCsvRowValidator(clock);
    }

    @Override
    public List<PortfolioPosition> parse(InputStream inputStream, String fileName) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new PortfolioImportException("Failed to read file: " + fileName, e);
        }

        // Strip UTF-8 BOM (0xEF 0xBB 0xBF)
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
        }

        // Pass 1: schema validation on header row only
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new PortfolioImportException("File is empty: " + fileName);
            }
            schemaValidator.validate(header);
        } catch (PortfolioImportException e) {
            throw e;
        } catch (CsvSchemaException e) {
            throw e;
        } catch (Exception e) {
            throw new PortfolioImportException("Failed to parse file: " + fileName, e);
        }

        // Pass 2: collect all data rows and run row validation
        List<String[]> dataRows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            reader.readNext(); // skip header
            String[] row;
            while ((row = reader.readNext()) != null) {
                dataRows.add(row);
            }
        } catch (Exception e) {
            throw new PortfolioImportException("Failed to parse file: " + fileName, e);
        }

        if (dataRows.isEmpty()) {
            throw new PortfolioImportException("No positions found in the uploaded file: " + fileName);
        }

        List<RowValidationError> rowErrors = rowValidator.validate(dataRows);
        if (!rowErrors.isEmpty()) {
            throw new CsvRowValidationException(rowErrors);
        }

        // Mapping pass — only reached when both tiers pass
        List<PortfolioPosition> positions = new ArrayList<>();
        for (String[] row : dataRows) {
            String costBasisStr = row.length > 8 ? row[8] : "";
            String unrealizedPnlStr = row.length > 9 ? row[9] : "";
            BigDecimal costBasis = (costBasisStr == null || costBasisStr.isBlank()) ? null : new BigDecimal(costBasisStr);
            BigDecimal unrealizedPnl = (unrealizedPnlStr == null || unrealizedPnlStr.isBlank()) ? null : new BigDecimal(unrealizedPnlStr);
            positions.add(PortfolioPosition.of(
                    row[0],
                    row[1],
                    row[2],
                    new BigDecimal(row[3]),
                    new BigDecimal(row[4]),
                    new BigDecimal(row[5]),
                    row[6],
                    LocalDate.parse(row[7]),
                    costBasis,
                    unrealizedPnl
            ));
        }
        return positions;
    }
}
