package com.finance.app.application.service;

import com.finance.app.domain.model.AccountFilterCriteria;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.FilterAccountsUseCase;
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
 * Failing tests for Feature B — Filter Bar (plan: PLAN_B_FilterBar.md, §4).
 *
 * RUNTIME FAILURE EXPECTED until:
 *  - AccountFilterCriteria moved to src/main/java (delete test-scope stub)
 *  - FilterAccountsUseCase moved to src/main/java (delete test-scope stub)
 *  - AccountApplicationService implements FilterAccountsUseCase
 *
 * Each test casts AccountApplicationService to FilterAccountsUseCase (non-final → compiles).
 * At runtime the cast throws ClassCastException until the production implementation lands.
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceFilterTest {

    @Mock private AccountFileParser parser;
    @Mock private AccountRepository repository;

    private AccountApplicationService sut;

    private BankAccount chase;
    private BankAccount chaseChecking;
    private BankAccount ally;
    private BankAccount bofa;

    @BeforeEach
    void setUp() {
        sut = new AccountApplicationService(parser, repository);
        chase        = BankAccount.create("Chase", "000111222", AccountType.SAVINGS,  new BigDecimal("500.00"), "USD");
        chaseChecking= BankAccount.create("Chase", "333444555", AccountType.CHECKING, new BigDecimal("200.00"), "USD");
        ally         = BankAccount.create("Ally",  "666777888", AccountType.SAVINGS,  new BigDecimal("800.00"), "USD");
        bofa         = BankAccount.create("BOFA",  "999000111", AccountType.CHECKING, new BigDecimal("150.00"), "USD");
    }

    // F-1: bankName="chase" (lowercase) → Chase accounts only
    @Test
    void filterAccounts_byBankNameLowercase_returnsMatchingAccounts() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria("chase", null, null);

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).extracting(BankAccount::bankName)
                .containsExactlyInAnyOrder("Chase", "Chase");
    }

    // F-2: bankName="CHA" uppercase partial → case-insensitive match → Chase accounts
    @Test
    void filterAccounts_byBankNameUppercasePartial_isCaseInsensitive() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria("CHA", null, null);

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).hasSize(2)
                .extracting(BankAccount::bankName)
                .containsOnly("Chase");
    }

    // F-3: accountType="SAV" → SAVINGS accounts only
    @Test
    void filterAccounts_byAccountTypePartial_returnsSavingsAccounts() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria(null, null, "SAV");

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).extracting(BankAccount::accountType)
                .containsOnly(AccountType.SAVINGS);
    }

    // F-4: bankName="chase" AND accountType="CHECKING" → Chase/CHECKING only (AND logic)
    @Test
    void filterAccounts_byBankNameAndAccountType_appliesAndLogic() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria("chase", null, "CHECKING");

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bankName()).isEqualTo("Chase");
        assertThat(result.get(0).accountType()).isEqualTo(AccountType.CHECKING);
    }

    // F-5: blank bankName → treated as no filter; all accounts returned
    @Test
    void filterAccounts_blankBankName_returnsAllAccounts() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria("", null, null);

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).hasSize(4);
    }

    // F-6: bankName="NOMATCHING" → no accounts match → empty list
    @Test
    void filterAccounts_noMatchingBankName_returnsEmptyList() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria("NOMATCHING", null, null);

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).isEmpty();
    }

    // F-7: accountNumber="000111" partial → matches Chase/SAVINGS (000111222)
    @Test
    void filterAccounts_byPartialAccountNumber_returnsMatchingAccounts() {
        given(repository.findAll()).willReturn(List.of(chase, chaseChecking, ally, bofa));
        var criteria = new AccountFilterCriteria(null, "000111", null);

        var result = ((FilterAccountsUseCase) sut).filterAccounts(criteria);

        assertThat(result).hasSize(1)
                .first().extracting(BankAccount::accountNumber).isEqualTo("000111222");
    }
}
