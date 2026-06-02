package com.finance.app.infrastructure.config;

import com.finance.app.domain.port.out.PasswordHasher;
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
}
