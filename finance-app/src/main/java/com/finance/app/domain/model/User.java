package com.finance.app.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class User {

    private final UserId userId;
    private final Username username;
    private final HashedPassword hashedPassword;
    private final Role role;
    private final LocalDateTime createdAt;

    public User(UserId userId, Username username, HashedPassword hashedPassword,
                Role role, LocalDateTime createdAt) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.username = Objects.requireNonNull(username, "username is required");
        this.hashedPassword = Objects.requireNonNull(hashedPassword, "hashedPassword is required");
        this.role = Objects.requireNonNull(role, "role is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static User create(UserId userId, Username username, HashedPassword hashedPassword, Role role) {
        return new User(userId, username, hashedPassword, role, LocalDateTime.now());
    }

    public UserId userId() { return userId; }
    public Username username() { return username; }
    public HashedPassword hashedPassword() { return hashedPassword; }
    public Role role() { return role; }
    public LocalDateTime createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "User{userId=" + userId + ", username=" + username + ", role=" + role + "}";
    }
}
