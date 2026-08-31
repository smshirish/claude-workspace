package com.finance.app.infrastructure.config;

import com.finance.app.application.service.AccountApplicationService;
import com.finance.app.domain.port.in.FilterAccountsUseCase;
import com.finance.app.domain.port.in.GetAllAccountsUseCase;
import com.finance.app.domain.port.in.ImportAccountsUseCase;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;
import com.finance.app.infrastructure.adapter.out.persistence.OpenCsvAccountParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class AccountBeanConfig {

    @Bean
    public AccountFileParser accountFileParser() {
        return new OpenCsvAccountParser();
    }

    // Each port gets its own @Bean method (rather than one shared AccountApplicationService bean)
    // so @MockBean on one port in older tests doesn't remove the others from the context.
    @Bean
    public ImportAccountsUseCase importAccountsUseCase(AccountFileParser parser,
                                                        @Lazy AccountRepository repository) {
        return new AccountApplicationService(parser, repository);
    }

    @Bean
    public GetAllAccountsUseCase getAllAccountsUseCase(AccountFileParser parser,
                                                        @Lazy AccountRepository repository) {
        return new AccountApplicationService(parser, repository);
    }

    @Bean
    public FilterAccountsUseCase filterAccountsUseCase(AccountFileParser parser,
                                                        @Lazy AccountRepository repository) {
        return new AccountApplicationService(parser, repository);
    }
}
