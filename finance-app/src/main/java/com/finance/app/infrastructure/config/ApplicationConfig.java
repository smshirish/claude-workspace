package com.finance.app.infrastructure.config;

import com.finance.app.application.service.AccountApplicationService;
import com.finance.app.domain.port.out.AccountFileParser;
import com.finance.app.domain.port.out.AccountRepository;
import com.finance.app.domain.port.out.PasswordHasher;
import com.finance.app.infrastructure.adapter.out.persistence.OpenCsvAccountParser;
import com.finance.app.infrastructure.adapter.out.security.BcryptPasswordHasherAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfig {

    @Bean
    public PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new BcryptPasswordHasherAdapter(passwordEncoder);
    }

    @Bean
    public AccountFileParser accountFileParser() {
        return new OpenCsvAccountParser();
    }

    @Bean
    public AccountApplicationService accountApplicationService(AccountFileParser parser,
                                                               AccountRepository repository) {
        return new AccountApplicationService(parser, repository);
    }
}
