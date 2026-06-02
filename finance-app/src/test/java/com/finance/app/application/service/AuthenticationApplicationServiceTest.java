package com.finance.app.application.service;

import com.finance.app.domain.exception.AuthenticationException;
import com.finance.app.domain.model.*;
import com.finance.app.domain.port.in.AuthenticateUserUseCase.AuthenticateCommand;
import com.finance.app.domain.port.out.PasswordHasher;
import com.finance.app.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationApplicationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHasher passwordHasher;

    private AuthenticationApplicationService sut;

    @BeforeEach
    void setUp() {
        sut = new AuthenticationApplicationService(userRepository, passwordHasher);
    }

    @Test
    void authenticate_withValidCredentials_returnsUser() {
        var hash = new HashedPassword("$2a$10$hash");
        var user = User.create(UserId.generate(), new Username("alice"), hash, Role.OWNER);
        given(userRepository.findByUsername(new Username("alice"))).willReturn(Optional.of(user));
        given(passwordHasher.matches("secret", hash)).willReturn(true);

        var result = sut.authenticate(new AuthenticateCommand("alice", "secret"));

        assertThat(result).isEqualTo(user);
    }

    @Test
    void authenticate_withWrongPassword_throwsAuthenticationException() {
        var hash = new HashedPassword("$2a$10$hash");
        var user = User.create(UserId.generate(), new Username("alice"), hash, Role.OWNER);
        given(userRepository.findByUsername(new Username("alice"))).willReturn(Optional.of(user));
        given(passwordHasher.matches("wrong", hash)).willReturn(false);

        assertThatThrownBy(() -> sut.authenticate(new AuthenticateCommand("alice", "wrong")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void authenticate_withUnknownUser_throwsAuthenticationException() {
        given(userRepository.findByUsername(new Username("ghost"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.authenticate(new AuthenticateCommand("ghost", "pass")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void authenticate_usernameIsCaseInsensitive() {
        var hash = new HashedPassword("$2a$10$hash");
        var user = User.create(UserId.generate(), new Username("alice"), hash, Role.OWNER);
        given(userRepository.findByUsername(new Username("ALICE"))).willReturn(Optional.of(user));
        given(passwordHasher.matches("secret", hash)).willReturn(true);

        assertThatCode(() -> sut.authenticate(new AuthenticateCommand("ALICE", "secret")))
                .doesNotThrowAnyException();
    }
}
