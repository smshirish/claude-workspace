package com.finance.app.domain.port.out;

import com.finance.app.domain.model.User;
import com.finance.app.domain.model.Username;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(Username username);

    void save(User user);
}
