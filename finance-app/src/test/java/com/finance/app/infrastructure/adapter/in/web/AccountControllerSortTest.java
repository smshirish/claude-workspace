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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Failing MockMvc tests for Feature A — Column Sorting (plan: PLAN_A_ColumnSorting.md).
 *
 * COMPILE FAILURE EXPECTED until:
 *  - AccountSortCriteria moved to src/main/java (delete test-scope stub)
 *  - GetAllAccountsUseCase.getAllAccounts() signature updated to getAllAccounts(AccountSortCriteria)
 *  - AccountController accepts @RequestParam sortField / sortDir and passes AccountSortCriteria to the use case
 *  - AccountController adds activeSortField and activeSortDir to the model
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerSortTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureStorageFiles(DynamicPropertyRegistry registry) {
        registry.add("finance.storage.users-file", () -> tempDir.resolve("users.csv").toString());
        registry.add("finance.storage.accounts-file", () -> tempDir.resolve("accounts.csv").toString());
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ImportAccountsUseCase importAccountsUseCase;

    @MockBean
    GetAllAccountsUseCase getAllAccountsUseCase;

    private static final BankAccount ALLY  = BankAccount.create("Ally",  "333444555", AccountType.CHECKING, new BigDecimal("100.00"), "USD");
    private static final BankAccount BOFA  = BankAccount.create("BOFA",  "666777888", AccountType.SAVINGS,  new BigDecimal("300.00"), "USD");
    private static final BankAccount CHASE = BankAccount.create("Chase", "000111222", AccountType.SAVINGS,  new BigDecimal("500.00"), "USD");

    // C-1: sortField=bankName, sortDir=asc → activeSortField=bankName, activeSortDir=asc, accounts in model
    @Test
    @WithMockUser
    void getAccounts_sortByBankNameAsc_setsCorrectModelAttributes() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(ALLY, BOFA, CHASE));

        mockMvc.perform(get("/accounts").param("sortField", "bankName").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts"))
                .andExpect(model().attribute("activeSortField", "bankName"))
                .andExpect(model().attribute("activeSortDir", "asc"))
                .andExpect(model().attributeExists("accounts"));
    }

    // C-2: sortField=balance, sortDir=desc → activeSortField=balance, activeSortDir=desc
    @Test
    @WithMockUser
    void getAccounts_sortByBalanceDesc_setsDescDirectionInModel() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE, BOFA, ALLY));

        mockMvc.perform(get("/accounts").param("sortField", "balance").param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("activeSortField", "balance"))
                .andExpect(model().attribute("activeSortDir", "desc"));
    }

    // C-3: no sort params → default sort applied; activeSortField=balance, activeSortDir=asc
    @Test
    @WithMockUser
    void getAccounts_noSortParams_appliesDefaultAndSetsBalanceAscInModel() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(eq(AccountSortCriteria.DEFAULT)))
                .willReturn(List.of(ALLY, BOFA, CHASE));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("activeSortField", "balance"))
                .andExpect(model().attribute("activeSortDir", "asc"));
    }

    // C-4: unknown sortField → silently falls back to default; page renders without error
    @Test
    @WithMockUser
    void getAccounts_unknownSortField_fallsBackToDefaultAndRendersWithoutError() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(eq(AccountSortCriteria.DEFAULT)))
                .willReturn(List.of(ALLY, BOFA, CHASE));

        mockMvc.perform(get("/accounts").param("sortField", "unknown").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts"))
                .andExpect(model().attribute("activeSortField", "balance"))
                .andExpect(model().attribute("activeSortDir", "asc"))
                .andExpect(model().attributeDoesNotExist("importError", "schemaError", "rowErrors"));
    }
}
