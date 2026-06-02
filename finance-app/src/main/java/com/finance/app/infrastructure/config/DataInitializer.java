package com.finance.app.infrastructure.config;

import com.finance.app.domain.model.Role;
import com.finance.app.domain.model.User;
import com.finance.app.domain.model.UserId;
import com.finance.app.domain.model.Username;
import com.finance.app.domain.port.out.PasswordHasher;
import com.finance.app.domain.port.out.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Value("${finance.security.default-admin-password:admin123}")
    private String defaultAdminPassword;

    public DataInitializer(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        var adminUsername = new Username("admin");
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            var admin = User.create(
                    UserId.generate(),
                    adminUsername,
                    passwordHasher.hash(defaultAdminPassword),
                    Role.OWNER
            );
            userRepository.save(admin);
            log.warn("*** Created default admin user. Username: admin | Password: {} — change this! ***",
                    defaultAdminPassword);
        }
    }
}
