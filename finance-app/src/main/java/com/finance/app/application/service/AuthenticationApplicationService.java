package com.finance.app.application.service;

import com.finance.app.domain.exception.AuthenticationException;
import com.finance.app.domain.model.User;
import com.finance.app.domain.model.Username;
import com.finance.app.domain.port.in.AuthenticateUserUseCase;
import com.finance.app.domain.port.out.PasswordHasher;
import com.finance.app.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationApplicationService implements AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticationApplicationService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public User authenticate(AuthenticateCommand command) {
        var username = new Username(command.username());
        return userRepository.findByUsername(username)
                .filter(user -> passwordHasher.matches(command.rawPassword(), user.hashedPassword()))
                .orElseThrow(() -> new AuthenticationException("Invalid credentials for user: " + command.username()));
    }
}
