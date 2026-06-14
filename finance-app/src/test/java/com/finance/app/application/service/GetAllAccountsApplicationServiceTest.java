package com.finance.app.application.service;

import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
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

@ExtendWith(MockitoExtension.class)
class GetAllAccountsApplicationServiceTest {

    @Mock private AccountFileParser parser;
    @Mock private AccountRepository repository;

    private AccountApplicationService sut;

    @BeforeEach
    void setUp() {
        sut = new AccountApplicationService(parser, repository);
    }

    // T3.1
    @Test
    void getAllAccounts_returnsDelegatedRepositoryList() {
        var accounts = List.of(
                BankAccount.create("ING", "NL91ABNA0417164300", AccountType.CHECKING, new BigDecimal("500.00"), "EUR"),
                BankAccount.create("Rabobank", "NL20INGB0001234567", AccountType.SAVINGS, new BigDecimal("1200.00"), "EUR")
        );
        given(repository.findAll()).willReturn(accounts);

        var result = sut.getAllAccounts();

        assertThat(result).isEqualTo(accounts);
    }

    // T3.2
    @Test
    void getAllAccounts_whenRepositoryEmpty_returnsEmptyListWithoutException() {
        given(repository.findAll()).willReturn(List.of());

        assertThatCode(() -> sut.getAllAccounts())
                .doesNotThrowAnyException();

        assertThat(sut.getAllAccounts()).isEmpty();
    }
}
