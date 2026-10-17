package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
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
import com.finance.app.domain.port.in.ImportPortfolioUseCase.ImportPortfolioCommand;
import com.finance.app.domain.port.out.AccountIdResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/portfolio")
public class PortfolioController {

    private static final Map<String, PortfolioSortField> SORT_FIELD_MAP = Map.of(
            "symbol",        PortfolioSortField.SYMBOL,
            "assetClass",    PortfolioSortField.ASSET_CLASS,
            "marketValue",   PortfolioSortField.MARKET_VALUE,
            "unrealizedPnl", PortfolioSortField.UNREALIZED_PNL
    );

    private final ImportPortfolioUseCase importPortfolioUseCase;
    private final GetLatestPortfolioUseCase getLatestPortfolioUseCase;
    private final FilterPortfolioUseCase filterPortfolioUseCase;
    private final AccountIdResolver accountIdResolver;
    private final Clock clock;

    public PortfolioController(ImportPortfolioUseCase importPortfolioUseCase,
                                GetLatestPortfolioUseCase getLatestPortfolioUseCase,
                                FilterPortfolioUseCase filterPortfolioUseCase,
                                AccountIdResolver accountIdResolver,
                                Clock clock) {
        this.importPortfolioUseCase = importPortfolioUseCase;
        this.getLatestPortfolioUseCase = getLatestPortfolioUseCase;
        this.filterPortfolioUseCase = filterPortfolioUseCase;
        this.accountIdResolver = accountIdResolver;
        this.clock = clock;
    }

    @GetMapping
    public String showPortfolio(
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false, defaultValue = "") String symbol,
            @RequestParam(required = false, defaultValue = "") String assetClass,
            Model model) {

        PortfolioSortField field = sortField != null ? SORT_FIELD_MAP.get(sortField) : null;
        PortfolioSortCriteria sortCriteria;
        String activeField;
        String activeDir;
        if (field != null) {
            SortDirection direction = "desc".equalsIgnoreCase(sortDir) ? SortDirection.DESC : SortDirection.ASC;
            sortCriteria = new PortfolioSortCriteria(field, direction);
            activeField = sortField;
            activeDir = direction == SortDirection.DESC ? "desc" : "asc";
        } else {
            sortCriteria = PortfolioSortCriteria.DEFAULT;
            activeField = "";
            activeDir = "asc";
        }

        Optional<PortfolioSnapshot> snapshotOpt;
        if (!symbol.isBlank() || !assetClass.isBlank()) {
            snapshotOpt = filterPortfolioUseCase.filterPositions(
                    new PortfolioFilterCriteria(symbol, assetClass), sortCriteria);
        } else {
            snapshotOpt = getLatestPortfolioUseCase.getLatestPositions(sortCriteria);
        }

        model.addAttribute("activeSortField", activeField);
        model.addAttribute("activeSortDir", activeDir);
        model.addAttribute("filterSymbol", symbol);
        model.addAttribute("filterAssetClass", assetClass);
        String clearFilterUrl = activeField.isEmpty()
                ? "/portfolio"
                : "/portfolio?sortField=" + activeField + "&sortDir=" + activeDir;
        model.addAttribute("clearFilterUrl", clearFilterUrl);

        if (snapshotOpt.isEmpty() || snapshotOpt.get().positions().isEmpty()) {
            model.addAttribute("emptyState", true);
        } else {
            PortfolioSnapshot snapshot = snapshotOpt.get();
            List<PortfolioPosition> positions = resolveIds(snapshot.positions());
            BigDecimal totalMarketValue = positions.stream()
                    .map(PortfolioPosition::marketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean isStale = snapshot.asOfDate().isBefore(LocalDate.now(clock).minusDays(14));
            model.addAttribute("positions", positions);
            model.addAttribute("asOfDate", snapshot.asOfDate());
            model.addAttribute("totalMarketValue", totalMarketValue);
            model.addAttribute("isStale", isStale);
        }

        return "portfolio";
    }

    @PostMapping("/import")
    public String importPortfolio(@RequestParam("file") MultipartFile file,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            LocalDate asOfDate = importPortfolioUseCase.importPortfolio(
                    new ImportPortfolioCommand(file.getInputStream(), file.getOriginalFilename()));
            redirectAttributes.addFlashAttribute("importSuccess",
                    "Portfolio imported — as of " + asOfDate);
            return "redirect:/portfolio";
        } catch (CsvSchemaException e) {
            model.addAttribute("schemaError", e.getSchemaError());
            repopulateSnapshot(model);
            return "portfolio";
        } catch (CsvRowValidationException e) {
            model.addAttribute("rowErrors", e.getRowErrors());
            repopulateSnapshot(model);
            return "portfolio";
        } catch (PortfolioImportException e) {
            model.addAttribute("importError", e.getMessage());
            repopulateSnapshot(model);
            return "portfolio";
        } catch (IOException e) {
            model.addAttribute("importError", "Could not read uploaded file.");
            repopulateSnapshot(model);
            return "portfolio";
        }
    }

    private void repopulateSnapshot(Model model) {
        model.addAttribute("activeSortField", "");
        model.addAttribute("activeSortDir", "asc");
        model.addAttribute("filterSymbol", "");
        model.addAttribute("filterAssetClass", "");
        model.addAttribute("clearFilterUrl", "/portfolio");
        Optional<PortfolioSnapshot> snapshotOpt =
                getLatestPortfolioUseCase.getLatestPositions(PortfolioSortCriteria.DEFAULT);
        if (snapshotOpt.isEmpty() || snapshotOpt.get().positions().isEmpty()) {
            model.addAttribute("emptyState", true);
        } else {
            PortfolioSnapshot snapshot = snapshotOpt.get();
            List<PortfolioPosition> positions = resolveIds(snapshot.positions());
            BigDecimal totalMarketValue = positions.stream()
                    .map(PortfolioPosition::marketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean isStale = snapshot.asOfDate().isBefore(LocalDate.now(clock).minusDays(14));
            model.addAttribute("positions", positions);
            model.addAttribute("asOfDate", snapshot.asOfDate());
            model.addAttribute("totalMarketValue", totalMarketValue);
            model.addAttribute("isStale", isStale);
        }
    }

    private List<PortfolioPosition> resolveIds(List<PortfolioPosition> positions) {
        return positions.stream()
                .map(p -> p.withResolvedAccountId(accountIdResolver.resolve(p.accountId())))
                .toList();
    }
}
