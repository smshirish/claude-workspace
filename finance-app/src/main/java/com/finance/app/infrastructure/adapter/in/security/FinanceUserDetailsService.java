package com.finance.app.infrastructure.adapter.in.security;

import com.finance.app.domain.model.Username;
import com.finance.app.domain.port.out.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class FinanceUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public FinanceUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var usernameVO = new Username(username);
        return userRepository.findByUsername(usernameVO)
                .map(user -> User.withUsername(user.username().value())
                        .password(user.hashedPassword().value())
                        .roles(user.role().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
