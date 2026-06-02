package com.finance.app.domain.port.in;

import com.finance.app.domain.model.User;

public interface AuthenticateUserUseCase {

    User authenticate(AuthenticateCommand command);

    record AuthenticateCommand(String username, String rawPassword) {}
}
