package com.finance.app.infrastructure.adapter.in.web;

import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.model.AccountSortCriteria;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.FilterAccountsUseCase;
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
 * MockMvc tests for Feature C — Filtered Total (PLAN_C_FilteredTotal.md §4).
 *
 * All tests fail until AccountController.showAccounts() computes and adds
 * totalBalance (BigDecimal) to the model.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTotalTest {

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
    @MockBean FilterAccountsUseCase filterAccountsUseCase;

    private static final BankAccount CHASE_SAVINGS  = BankAccount.create("Chase", "000111222", AccountType.SAVINGS,  new BigDecimal("100.00"), "USD");
    private static final BankAccount CHASE_CHECKING = BankAccount.create("Chase", "333444555", AccountType.CHECKING, new BigDecimal("200.00"), "USD");
    private static final BankAccount ALLY_SAVINGS   = BankAccount.create("Ally",  "666777888", AccountType.SAVINGS,  new BigDecimal("300.00"), "USD");

    // T-1: no filter, 3 accounts → totalBalance = 600
    @Test
    @WithMockUser
    void getAccounts_noFilter_totalEqualsGrandSum() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING, ALLY_SAVINGS));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalBalance", comparesEqualTo(new BigDecimal("600.00"))));
    }

    // T-2: bankName=chase filter → only Chase accounts summed → totalBalance = 300
    @Test
    @WithMockUser
    void getAccounts_bankNameFilter_totalReflectsFilteredAccounts() throws Exception {
        given(filterAccountsUseCase.filterAccounts(any(AccountFilterCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, CHASE_CHECKING));

        mockMvc.perform(get("/accounts").param("bankName", "chase"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalBalance", comparesEqualTo(new BigDecimal("300.00"))));
    }

    // T-3: bankName=NOMATCHING → empty result → totalBalance = 0.00
    @Test
    @WithMockUser
    void getAccounts_noMatchingFilter_totalIsZero() throws Exception {
        given(filterAccountsUseCase.filterAccounts(any(AccountFilterCriteria.class)))
                .willReturn(List.of());

        mockMvc.perform(get("/accounts").param("bankName", "NOMATCHING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalBalance", comparesEqualTo(BigDecimal.ZERO)));
    }

    // T-4: empty repository, no accounts → totalBalance = 0.00
    @Test
    @WithMockUser
    void getAccounts_emptyRepository_totalIsZero() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of());

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalBalance", comparesEqualTo(BigDecimal.ZERO)));
    }

    // T-5: sort only (no filter) → totalBalance = grand total, sort does not affect sum
    @Test
    @WithMockUser
    void getAccounts_sortOnlyNoFilter_totalEqualsGrandSum() throws Exception {
        given(getAllAccountsUseCase.getAllAccounts(any(AccountSortCriteria.class)))
                .willReturn(List.of(CHASE_SAVINGS, ALLY_SAVINGS, CHASE_CHECKING));

        mockMvc.perform(get("/accounts").param("sortField", "balance").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalBalance", comparesEqualTo(new BigDecimal("600.00"))));
    }
}
