package com.finance.app.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void create_setsAllFieldsAndTimestamps() {
        var userId = UserId.generate();
        var username = new Username("alice");
        var hash = new HashedPassword("$2a$10$hash");

        var user = User.create(userId, username, hash, Role.OWNER);

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.username()).isEqualTo(username);
        assertThat(user.hashedPassword()).isEqualTo(hash);
        assertThat(user.role()).isEqualTo(Role.OWNER);
        assertThat(user.createdAt()).isNotNull().isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void equals_isBasedOnUserIdOnly() {
        var userId = UserId.generate();
        var user1 = User.create(userId, new Username("alice"), new HashedPassword("$2a$10$hash1"), Role.OWNER);
        var user2 = User.create(userId, new Username("alice"), new HashedPassword("$2a$10$hash2"), Role.OWNER);

        assertThat(user1).isEqualTo(user2);
    }

    @Test
    void equals_withDifferentUserIds_returnsFalse() {
        var user1 = User.create(UserId.generate(), new Username("alice"), new HashedPassword("$2a$10$h"), Role.OWNER);
        var user2 = User.create(UserId.generate(), new Username("alice"), new HashedPassword("$2a$10$h"), Role.OWNER);

        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    void constructor_withNullUserId_throwsNpe() {
        assertThatThrownBy(() ->
                new User(null, new Username("alice"), new HashedPassword("$2a$10$h"), Role.OWNER, LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullUsername_throwsNpe() {
        assertThatThrownBy(() ->
                new User(UserId.generate(), null, new HashedPassword("$2a$10$h"), Role.OWNER, LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
