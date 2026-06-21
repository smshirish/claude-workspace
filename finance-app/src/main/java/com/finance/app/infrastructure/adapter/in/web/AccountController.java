package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.exception.CsvRowValidationException;
import com.finance.app.domain.exception.CsvSchemaException;
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

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final ImportAccountsUseCase importAccountsUseCase;
    private final GetAllAccountsUseCase getAllAccountsUseCase;

    public AccountController(ImportAccountsUseCase importAccountsUseCase,
                             GetAllAccountsUseCase getAllAccountsUseCase) {
        this.importAccountsUseCase = importAccountsUseCase;
        this.getAllAccountsUseCase = getAllAccountsUseCase;
    }

    @GetMapping
    public String showAccounts(Model model) {
        model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts());
        return "accounts";
    }

    @PostMapping("/import")
    public String importAccounts(@RequestParam("file") MultipartFile file, Model model) {
        try {
            importAccountsUseCase.importAccounts(
                    new ImportAccountsCommand(file.getInputStream(), file.getOriginalFilename()));
            return "redirect:/accounts";
        } catch (CsvSchemaException e) {
            model.addAttribute("schemaError", e.getSchemaError());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts());
            return "accounts";
        } catch (CsvRowValidationException e) {
            model.addAttribute("rowErrors", e.getRowErrors());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts());
            return "accounts";
        } catch (AccountImportException e) {
            model.addAttribute("importError", e.getMessage());
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts());
            return "accounts";
        } catch (IOException e) {
            model.addAttribute("importError", "Could not read uploaded file.");
            model.addAttribute("accounts", getAllAccountsUseCase.getAllAccounts());
            return "accounts";
        }
    }
}
