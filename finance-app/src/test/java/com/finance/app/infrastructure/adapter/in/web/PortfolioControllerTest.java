package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
import com.finance.app.domain.exception.PortfolioImportException;
import com.finance.app.domain.model.PortfolioPosition;
import com.finance.app.domain.model.PortfolioSnapshot;
import com.finance.app.domain.model.PortfolioSortCriteria;
import com.finance.app.domain.model.validation.RowValidationError;
import com.finance.app.domain.port.in.FilterPortfolioUseCase;
import com.finance.app.domain.port.in.GetLatestPortfolioUseCase;
import com.finance.app.domain.port.in.ImportPortfolioUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Failing MockMvc tests for Feature PortfolioView — PortfolioController (plan §4).
 *
 * RUNTIME FAILURE EXPECTED until PortfolioController is moved to src/main/java
 * and fully implemented. All GET /portfolio requests currently return 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureStorageFiles(DynamicPropertyRegistry registry) {
        registry.add("finance.storage.users-file",  () -> tempDir.resolve("users.csv").toString());
        registry.add("finance.storage.accounts-file", () -> tempDir.resolve("accounts.csv").toString());
        registry.add("finance.storage.portfolio-dir", () -> tempDir.resolve("portfolio").toString());
    }

    @Autowired MockMvc mockMvc;

    @MockBean ImportPortfolioUseCase importPortfolioUseCase;
    @MockBean GetLatestPortfolioUseCase getLatestPortfolioUseCase;
    @MockBean FilterPortfolioUseCase filterPortfolioUseCase;

    private static final LocalDate DATE_2024_11_01 = LocalDate.of(2024, 11, 1);

    private static PortfolioPosition position(String symbol, BigDecimal marketValue) {
        return PortfolioPosition.of("U1", symbol, "STK",
                BigDecimal.ONE, marketValue, marketValue, "USD", DATE_2024_11_01, null, null);
    }

    // C-1: getLatestPositions returns empty → emptyState = true; positions absent; HTTP 200
    @Test
    @WithMockUser
    void getPortfolio_noSnapshot_setsEmptyStateTrue() throws Exception {
        given(getLatestPortfolioUseCase.getLatestPositions(any(PortfolioSortCriteria.class)))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/portfolio"))
                .andExpect(status().isOk())
                .andExpect(view().name("portfolio"))
                .andExpect(model().attribute("emptyState", true))
                .andExpect(model().attributeDoesNotExist("positions"));
    }

    // C-2: getLatestPositions returns snapshot → positions present; emptyState absent; asOfDate present
    @Test
    @WithMockUser
    void getPortfolio_withSnapshot_setsPositionsAndAsOfDate() throws Exception {
        var snapshot = new PortfolioSnapshot(DATE_2024_11_01,
                List.of(position("AAPL", new BigDecimal("1500.00"))));
        given(getLatestPortfolioUseCase.getLatestPositions(any(PortfolioSortCriteria.class)))
                .willReturn(Optional.of(snapshot));

        mockMvc.perform(get("/portfolio"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("positions"))
                .andExpect(model().attributeExists("asOfDate"))
                .andExpect(model().attributeDoesNotExist("emptyState"));
    }

    // C-3: snapshot date 15 days ago → isStale = true
    @Test
    @WithMockUser
    void getPortfolio_snapshotDate15DaysAgo_isStaleTrue() throws Exception {
        LocalDate staleDate = LocalDate.now().minusDays(15);
        var snapshot = new PortfolioSnapshot(staleDate,
                List.of(position("AAPL", new BigDecimal("1500.00"))));
        given(getLatestPortfolioUseCase.getLatestPositions(any(PortfolioSortCriteria.class)))
                .willReturn(Optional.of(snapshot));

        mockMvc.perform(get("/portfolio"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isStale", true));
    }

    // C-4: snapshot date 10 days ago → isStale = false
    @Test
    @WithMockUser
    void getPortfolio_snapshotDate10DaysAgo_isStaleFalse() throws Exception {
        LocalDate recentDate = LocalDate.now().minusDays(10);
        var snapshot = new PortfolioSnapshot(recentDate,
                List.of(position("AAPL", new BigDecimal("1500.00"))));
        given(getLatestPortfolioUseCase.getLatestPositions(any(PortfolioSortCriteria.class)))
                .willReturn(Optional.of(snapshot));

        mockMvc.perform(get("/portfolio"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isStale", false));
    }

    // C-5: POST import throws CsvSchemaException → schemaError set; rowErrors absent
    @Test
    @WithMockUser
    void importPortfolio_csvSchemaException_setsSchemaError() throws Exception {
        var csvFile = new MockMultipartFile("file", "bad.csv", "text/csv",
                "bad,header\nrow1\n".getBytes());
        given(importPortfolioUseCase.importPortfolio(any()))
                .willThrow(new CsvSchemaException("bad header"));
        given(getLatestPortfolioUseCase.getLatestPositions(any())).willReturn(Optional.empty());

        mockMvc.perform(multipart("/portfolio/import").file(csvFile).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("schemaError", "bad header"))
                .andExpect(model().attributeDoesNotExist("rowErrors"));
    }

    // C-6: POST import throws CsvRowValidationException with 1 error → rowErrors present; schemaError absent
    @Test
    @WithMockUser
    void importPortfolio_csvRowValidationException_setsRowErrors() throws Exception {
        var csvFile = new MockMultipartFile("file", "bad-rows.csv", "text/csv",
                "data\n".getBytes());
        var errors = List.of(new RowValidationError(1, "Quantity", "not a valid decimal"));
        given(importPortfolioUseCase.importPortfolio(any()))
                .willThrow(new CsvRowValidationException(errors));
        given(getLatestPortfolioUseCase.getLatestPositions(any())).willReturn(Optional.empty());

        mockMvc.perform(multipart("/portfolio/import").file(csvFile).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rowErrors", hasSize(1)))
                .andExpect(model().attributeDoesNotExist("schemaError"));
    }

    // C-7: POST import throws PortfolioImportException("No positions") → importError set; rowErrors absent
    @Test
    @WithMockUser
    void importPortfolio_portfolioImportException_setsImportError() throws Exception {
        var csvFile = new MockMultipartFile("file", "empty.csv", "text/csv",
                "header\n".getBytes());
        given(importPortfolioUseCase.importPortfolio(any()))
                .willThrow(new PortfolioImportException("No positions"));
        given(getLatestPortfolioUseCase.getLatestPositions(any())).willReturn(Optional.empty());

        mockMvc.perform(multipart("/portfolio/import").file(csvFile).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("importError", "No positions"))
                .andExpect(model().attributeDoesNotExist("rowErrors"));
    }

    // C-8: POST import succeeds (returns 2024-11-01) → redirect to /portfolio; flash importSuccess set
    @Test
    @WithMockUser
    void importPortfolio_success_redirectsWithImportSuccessFlash() throws Exception {
        var csvFile = new MockMultipartFile("file", "portfolio.csv", "text/csv",
                "data\n".getBytes());
        given(importPortfolioUseCase.importPortfolio(any())).willReturn(DATE_2024_11_01);

        mockMvc.perform(multipart("/portfolio/import").file(csvFile).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portfolio"))
                .andExpect(flash().attributeExists("importSuccess"));
    }
}
