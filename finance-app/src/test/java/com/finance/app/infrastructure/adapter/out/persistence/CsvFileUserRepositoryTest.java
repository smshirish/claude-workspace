package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CsvFileUserRepositoryTest {

    @TempDir
    Path tempDir;

    CsvFileUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CsvFileUserRepository(tempDir.resolve("users.csv").toString());
    }

    @Test
    void findByUsername_whenFileDoesNotExist_returnsEmpty() {
        assertThat(repository.findByUsername(new Username("anyone"))).isEmpty();
    }

    @Test
    void findByUsername_whenUserExists_returnsUser() {
        var user = aUser("alice");
        repository.save(user);

        var found = repository.findByUsername(new Username("alice"));

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo(new Username("alice"));
        assertThat(found.get().role()).isEqualTo(Role.OWNER);
    }

    @Test
    void findByUsername_whenUserDoesNotExist_returnsEmpty() {
        repository.save(aUser("alice"));

        assertThat(repository.findByUsername(new Username("bob"))).isEmpty();
    }

    @Test
    void save_persistsMultipleUsers() {
        repository.save(aUser("alice"));
        repository.save(aUser("bob"));

        assertThat(repository.findByUsername(new Username("alice"))).isPresent();
        assertThat(repository.findByUsername(new Username("bob"))).isPresent();
    }

    @Test
    void save_updatesExistingUserById() {
        var userId = UserId.generate();
        var original = new User(userId, new Username("alice"),
                new HashedPassword("$2a$10$oldhash"), Role.OWNER, LocalDateTime.now());
        repository.save(original);

        var updated = new User(userId, new Username("alice"),
                new HashedPassword("$2a$10$newhash"), Role.OWNER, LocalDateTime.now());
        repository.save(updated);

        var found = repository.findByUsername(new Username("alice"));
        assertThat(found.get().hashedPassword().value()).isEqualTo("$2a$10$newhash");
    }

    @Test
    void findByUsername_isCaseInsensitive() {
        repository.save(aUser("alice"));

        assertThat(repository.findByUsername(new Username("ALICE"))).isPresent();
    }

    private User aUser(String username) {
        return User.create(UserId.generate(), new Username(username),
                new HashedPassword("$2a$10$testhash"), Role.OWNER);
    }
}
