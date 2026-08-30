package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.model.AccountSortCriteria;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Failing MockMvc tests for Feature B — Filter Bar (plan: PLAN_B_FilterBar.md, §4).
 *
 * RUNTIME FAILURE EXPECTED until:
 *  - FilterAccountsUseCase added to src/main/java (delete test-scope stub after)
 *  - AccountController accepts bankName/accountNumber/accountType @RequestParam
 *  - AccountController calls FilterAccountsUseCase when any filter param is non-blank
 *  - AccountController sets filterBankName, filterAccountNumber, filterAccountType in model
 *  - AccountController sets clearFilterUrl in model
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerFilterTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureStorageFiles(DynamicPropertyRegistry registry) {
        registry.add("finance.storage.users-file", () -> tempDir.resolve("users.csv").toString());
        registry.add("finance.storage.accounts-file", () -> tempDir.resolve("accounts.csv").toString());
    }

    @Autowired MockMvc mockMvc;
    @MockBean ImportAccountsUseCase importAccountsUseCase;
    @MockBean GetAllAccountsUseCase getAllAccountsUseCase;

    private static final BankAccount CHASE_SAVINGS  = BankAccount.create("Chase", "000111222", AccountType.SAVINGS,  new BigDecimal("500.00"), "USD");
    private static final BankAccount CHASE_CHECKING = BankAccount.create("Chase", "333444555", AccountType.CHECKING, new BigDecimal("200.00"), "USD");
    private static final BankAccount ALLY_SAVINGS   = BankAccount.create("Ally",  "666777888", AccountType.SAVINGS,  new BigDecimal("800.00"), "USD");
    private static final BankAccount BOFA_CHECKING  = BankAccount.create("BOFA",  "999000111", AccountType.CHECKING, new BigDecimal("150.00"), "USD");

    // C-1: bankName=chase → only Chase rows; model has filterBankName=chase
    @Test
    @WithMockUser
    void getAccounts_filterByBankName_returnsFilteredRowsAndSetsModelAttr() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS, BOFA_CHECKING));

        mockMvc.perform(get("/accounts").param("bankName", "chase"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts"))
                .andExpect(model().attribute("filterBankName", "chase"))   // FAILS — controller does not set this yet
                .andExpect(model().attribute("accounts", hasSize(2)));     // FAILS — unfiltered list returned
    }

    // C-2: bankName="" (blank) + accountType=SAVINGS → filter by type only; filterBankName="" in model
    @Test
    @WithMockUser
    void getAccounts_blankBankNameAndAccountTypeFilter_filtersOnlyByType() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS, BOFA_CHECKING));

        mockMvc.perform(get("/accounts").param("bankName", "").param("accountType", "SAVINGS"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBankName", ""))        // FAILS — not set
                .andExpect(model().attribute("filterAccountType", "SAVINGS")); // FAILS — not set
    }

    // C-3: bankName=NOMATCHING → empty accounts list; empty-state element rendered
    @Test
    @WithMockUser
    void getAccounts_noMatchingBankName_rendersEmptyState() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS, BOFA_CHECKING));

        mockMvc.perform(get("/accounts").param("bankName", "NOMATCHING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBankName", "NOMATCHING")) // FAILS — not set
                .andExpect(model().attribute("accounts", empty()));            // FAILS — unfiltered list returned
    }

    // C-4: bankName=chase + sortField=balance&sortDir=asc → filtered Chase rows sorted by balance asc
    @Test
    @WithMockUser
    void getAccounts_filterAndSort_appliesFilterThenSort() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS, BOFA_CHECKING));

        mockMvc.perform(get("/accounts")
                        .param("bankName", "chase")
                        .param("sortField", "balance")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBankName", "chase"))  // FAILS — not set
                .andExpect(model().attribute("accounts", hasSize(2)));    // FAILS — unfiltered
    }

    // C-5: no params → all accounts; all filter model attrs present as empty string
    @Test
    @WithMockUser
    void getAccounts_noParams_allAccountsReturnedAndFilterAttrsAreEmpty() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS, BOFA_CHECKING));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("accounts", hasSize(4)))
                .andExpect(model().attribute("filterBankName", ""))        // FAILS — not set
                .andExpect(model().attribute("filterAccountType", ""))     // FAILS — not set
                .andExpect(model().attribute("filterAccountNumber", "")); // FAILS — not set
    }
}
