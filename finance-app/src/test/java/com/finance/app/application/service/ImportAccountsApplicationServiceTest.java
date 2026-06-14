package com.finance.app.application.service;

import com.finance.app.domain.exception.AccountImportException;
import com.finance.app.domain.model.AccountType;
import com.finance.app.domain.model.BankAccount;
import com.finance.app.domain.port.in.ImportAccountsUseCase.ImportAccountsCommand;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ImportAccountsApplicationServiceTest {

    @Mock private AccountFileParser parser;
    @Mock private AccountRepository repository;

    private AccountApplicationService sut;

    @BeforeEach
    void setUp() {
        sut = new AccountApplicationService(parser, repository);
    }

    // T2.1
    @Test
    void importAccounts_delegatesToParserAndSavesResult() {
        var accounts = List.of(anAccount(), anAccount());
        var command = aCommand();
        given(parser.parse(any(InputStream.class), eq("accounts.csv"))).willReturn(accounts);

        var result = sut.importAccounts(command);

        then(repository).should().saveAll(accounts);
        assertThat(result).isEqualTo(accounts);
    }

    // T2.2
    @Test
    void importAccounts_callsDeleteAllBeforeSaveAll() {
        var accounts = List.of(anAccount());
        given(parser.parse(any(InputStream.class), anyString())).willReturn(accounts);

        sut.importAccounts(aCommand());

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).deleteAll();
        inOrder.verify(repository).saveAll(accounts);
    }

    // T2.3
    @Test
    void importAccounts_whenParserReturnsEmptyList_throwsAccountImportException() {
        given(parser.parse(any(InputStream.class), anyString())).willReturn(List.of());

        assertThatThrownBy(() -> sut.importAccounts(aCommand()))
                .isInstanceOf(AccountImportException.class);
    }

    // T2.4
    @Test
    void importAccounts_whenParserReturnsEmptyList_doesNotCallSaveAll() {
        given(parser.parse(any(InputStream.class), anyString())).willReturn(List.of());

        assertThatThrownBy(() -> sut.importAccounts(aCommand()))
                .isInstanceOf(AccountImportException.class);

        then(repository).should(never()).saveAll(any());
    }

    private ImportAccountsCommand aCommand() {
        return new ImportAccountsCommand(new ByteArrayInputStream(new byte[0]), "accounts.csv");
    }

    private BankAccount anAccount() {
        return BankAccount.create("ING", "NL91ABNA0417164300", AccountType.CHECKING, new BigDecimal("100.00"), "EUR");
    }
}
