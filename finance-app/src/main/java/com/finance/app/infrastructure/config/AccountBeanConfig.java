package com.finance.app.infrastructure.config;

import com.finance.app.application.service.AccountApplicationService;
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

    @Bean
    public AccountApplicationService accountApplicationService(AccountFileParser parser,
                                                               @Lazy AccountRepository repository) {
        return new AccountApplicationService(parser, repository);
    }
}
