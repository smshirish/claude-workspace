package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.model.AccountSortCriteria;
import com.finance.app.domain.model.AccountSortField;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.model.SortDirection;
import com.finance.app.domain.port.in.FilterAccountsUseCase;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase.ImportAccountsCommand;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private static final Map<String, AccountSortField> SORT_FIELD_MAP = Map.of(
            "bankName",    AccountSortField.BANK_NAME,
            "balance",     AccountSortField.BALANCE,
            "accountType", AccountSortField.ACCOUNT_TYPE
    );

    private final ImportAccountsUseCase importAccountsUseCase;
    private final GetAllAccountsUseCase getAllAccountsUseCase;
    private final FilterAccountsUseCase filterAccountsUseCase;

    public AccountController(ImportAccountsUseCase importAccountsUseCase,
                             GetAllAccountsUseCase getAllAccountsUseCase,
                             FilterAccountsUseCase filterAccountsUseCase) {
        this.importAccountsUseCase = importAccountsUseCase;
        this.getAllAccountsUseCase = getAllAccountsUseCase;
        this.filterAccountsUseCase = filterAccountsUseCase;
    }

    @GetMapping
    public String showAccounts(
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false, defaultValue = "") String bankName,
            @RequestParam(required = false, defaultValue = "") String accountNumber,
            @RequestParam(required = false, defaultValue = "") String accountType,
            Model model) {
        AccountSortField field = sortField != null ? SORT_FIELD_MAP.get(sortField) : null;
        AccountSortCriteria criteria;
        String activeField;
        String activeDir;
        if (field != null) {
            SortDirection direction = "desc".equalsIgnoreCase(sortDir) ? SortDirection.DESC : SortDirection.ASC;
            criteria = new AccountSortCriteria(field, direction);
            activeField = sortField;
            activeDir = direction == SortDirection.DESC ? "desc" : "asc";
        } else {
            criteria = AccountSortCriteria.DEFAULT;
            activeField = "balance";
            activeDir = "asc";
        }

        List<BankAccount> accounts;
        if (!bankName.isBlank() || !accountNumber.isBlank() || !accountType.isBlank()) {
            accounts = sortAccounts(
                    filterAccountsUseCase.filterAccounts(new AccountFilterCriteria(bankName, accountNumber, accountType)),
                    criteria);
        } else {
            accounts = getAllAccountsUseCase.getAllAccounts(criteria);
        }

        BigDecimal totalBalance = accounts.stream()
                .map(BankAccount::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("accounts", accounts);
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("activeSortField", activeField);
        model.addAttribute("activeSortDir", activeDir);
        model.addAttribute("filterBankName", bankName);
        model.addAttribute("filterAccountNumber", accountNumber);
        model.addAttribute("filterAccountType", accountType);
        model.addAttribute("clearFilterUrl", "/accounts?sortField=" + activeField + "&sortDir=" + activeDir);
        return "accounts";
    }

    private static List<BankAccount> sortAccounts(List<BankAccount> accounts, AccountSortCriteria criteria) {
        Comparator<BankAccount> comparator = switch (criteria.field()) {
            case BANK_NAME    -> Comparator.comparing(BankAccount::bankName);
            case BALANCE      -> Comparator.comparing(BankAccount::balance);
            case ACCOUNT_TYPE -> Comparator.comparing(a -> a.accountType().name());
        };
        if (criteria.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return accounts.stream().sorted(comparator).toList();
    }

    @PostMapping("/import")
    public String importAccounts(@RequestParam("file") MultipartFile file, Model model) {
        try {
            importAccountsUseCase.importAccounts(
                    new ImportAccountsCommand(file.getInputStream(), file.getOriginalFilename()));
            return "redirect:/accounts";
        } catch (CsvSchemaException e) {
            model.addAttribute("schemaError", e.getSchemaError());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts(AccountSortCriteria.DEFAULT));
            return "accounts";
        } catch (CsvRowValidationException e) {
            model.addAttribute("rowErrors", e.getRowErrors());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts(AccountSortCriteria.DEFAULT));
            return "accounts";
        } catch (AccountImportException e) {
            model.addAttribute("importError", e.getMessage());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts(AccountSortCriteria.DEFAULT));
            return "accounts";
        } catch (IOException e) {
            model.addAttribute("importError", "Could not read uploaded file.");
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts(AccountSortCriteria.DEFAULT));
            return "accounts";
        }
    }
}
