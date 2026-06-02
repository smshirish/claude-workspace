package com.finance.app.domain.port.out;

import com.finance.app.domain.model.HashedPassword;

public interface PasswordHasher {

    HashedPassword hash(String rawPassword);

    boolean matches(String rawPassword, HashedPassword hashedPassword);
}
