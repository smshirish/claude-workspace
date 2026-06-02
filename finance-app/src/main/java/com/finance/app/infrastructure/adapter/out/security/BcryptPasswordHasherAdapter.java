package com.finance.app.infrastructure.adapter.out.security;

import com.finance.app.domain.model.HashedPassword;
import com.finance.app.domain.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

public class BcryptPasswordHasherAdapter implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordHasherAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public HashedPassword hash(String rawPassword) {
        return new HashedPassword(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, HashedPassword hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword.value());
    }
}
