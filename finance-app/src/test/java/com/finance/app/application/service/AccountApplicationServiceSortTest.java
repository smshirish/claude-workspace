package com.finance.app.application.service;

import com.finance.app.domain.model.AccountSortCriteria;
import com.finance.app.domain.model.AccountSortField;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.model.SortDirection;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * Failing tests for Feature A — Column Sorting (plan: PLAN_A_ColumnSorting.md).
 *
 * COMPILE FAILURE EXPECTED until:
 *  - AccountSortField / SortDirection / AccountSortCriteria moved to src/main/java
 *  - GetAllAccountsUseCase.getAllAccounts() signature changed to getAllAccounts(AccountSortCriteria)
 *  - AccountApplicationService implements the updated signature
 *
 * Stub domain classes are in src/test/java/.../domain/model/ — delete those stubs once
 * the real classes land in src/main/java.
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceSortTest {

    @Mock private AccountFileParser parser;
    @Mock private AccountRepository repository;

    private AccountApplicationService sut;

    private BankAccount chase;
    private BankAccount ally;
    private BankAccount bofa;

    @BeforeEach
    void setUp() {
        sut = new AccountApplicationService(parser, repository);
        // Fixture: Chase/SAVINGS/500, Ally/CHECKING/100, BOFA/SAVINGS/300
        chase = BankAccount.create("Chase", "000111222", AccountType.SAVINGS,  new BigDecimal("500.00"), "USD");
        ally  = BankAccount.create("Ally",  "333444555", AccountType.CHECKING, new BigDecimal("100.00"), "USD");
        bofa  = BankAccount.create("BOFA",  "666777888", AccountType.SAVINGS,  new BigDecimal("300.00"), "USD");
        given(repository.findAll()).willReturn(List.of(chase, ally, bofa));
    }

    // S-1: bankName ASC → Ally, BOFA, Chase
    @Test
    void getAllAccounts_sortByBankNameAsc_returnsAlphabeticalOrder() {
        var criteria = new AccountSortCriteria(AccountSortField.BANK_NAME, SortDirection.ASC);

        var result = sut.getAllAccounts(criteria);

        assertThat(result).extracting(BankAccount::bankName)
                .containsExactly("Ally", "BOFA", "Chase");
    }

    // S-2: bankName DESC → Chase, BOFA, Ally
    @Test
    void getAllAccounts_sortByBankNameDesc_returnsReverseAlphabeticalOrder() {
        var criteria = new AccountSortCriteria(AccountSortField.BANK_NAME, SortDirection.DESC);

        var result = sut.getAllAccounts(criteria);

        assertThat(result).extracting(BankAccount::bankName)
                .containsExactly("Chase", "BOFA", "Ally");
    }

    // S-3: balance ASC → 100, 300, 500
    @Test
    void getAllAccounts_sortByBalanceAsc_returnsLowestFirst() {
        var criteria = new AccountSortCriteria(AccountSortField.BALANCE, SortDirection.ASC);

        var result = sut.getAllAccounts(criteria);

        assertThat(result).extracting(BankAccount::balance)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("300.00"), new BigDecimal("500.00"));
    }

    // S-4: accountType ASC → CHECKING before SAVINGS (C < S lexicographically)
    @Test
    void getAllAccounts_sortByAccountTypeAsc_returnsCheckingBeforeSavings() {
        var criteria = new AccountSortCriteria(AccountSortField.ACCOUNT_TYPE, SortDirection.ASC);

        var result = sut.getAllAccounts(criteria);

        assertThat(result).first()
                .extracting(BankAccount::accountType)
                .isEqualTo(AccountType.CHECKING);
    }

    // S-5: DEFAULT criteria (BALANCE ASC) → 100, 300, 500
    @Test
    void getAllAccounts_withDefaultCriteria_returnsByBalanceAscending() {
        var result = sut.getAllAccounts(AccountSortCriteria.DEFAULT);

        assertThat(result).extracting(BankAccount::balance)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("300.00"), new BigDecimal("500.00"));
    }
}
