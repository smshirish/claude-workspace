package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ImportAccountsUseCase importAccountsUseCase;

    @MockBean
    GetAllAccountsUseCase getAllAccountsUseCase;

    // T6.1
    @Test
    @WithMockUser
    void getAccounts_returns200AndAccountsModelAttribute() throws Exception {
        var accounts = List.of(
                BankAccount.create("ING", "NL91ABNA0417164300", AccountType.CHECKING, new BigDecimal("500.00"), "EUR")
        );
        given(getAllAccountsUseCase.getAllAccounts()).willReturn(accounts);

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts"))
                .andExpect(model().attributeExists("accounts"));
    }

    // T6.2
    @Test
    @WithMockUser
    void getAccounts_whenNoAccountsStored_renders200WithoutError() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts()).willReturn(List.of());

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts"))
                .andExpect(model().attribute("accounts", List.of()));
    }

    // T6.3
    @Test
    @WithMockUser
    void importAccounts_withValidCsvFile_callsUseCaseAndRedirects() throws Exception {
        var csvFile = new MockMultipartFile(
                "file", "accounts.csv", "text/csv",
                "bankName,accountNumber,accountType,balance,currency\nING,ACC1,CHECKING,100,EUR".getBytes()
        );
        given(importAccountsUseCase.importAccounts(any())).willReturn(List.of(
                BankAccount.create("ING", "ACC1", AccountType.CHECKING, BigDecimal.ONE, "EUR")
        ));

        mockMvc.perform(multipart("/accounts/import").file(csvFile).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts"));
    }

    // T6.4
    @Test
    @WithMockUser
    void importAccounts_whenUseCaseThrows_rendersErrorResponse() throws Exception {
        var emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv",
                "bankName,accountNumber,accountType,balance,currency\n".getBytes()
        );
        given(importAccountsUseCase.importAccounts(any()))
                .willThrow(new AccountImportException("No accounts found in file"));

        mockMvc.perform(multipart("/accounts/import").file(emptyFile).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("importError"));
    }

    // T6.5
    @Test
    void getAccounts_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
