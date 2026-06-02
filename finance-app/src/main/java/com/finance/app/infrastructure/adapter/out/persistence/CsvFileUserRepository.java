package com.finance.app.infrastructure.adapter.out.persistence;

import com.finance.app.domain.exception.RepositoryException;
import com.finance.app.domain.model.*;
import com.finance.app.domain.port.out.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CsvFileUserRepository implements UserRepository {

    private static final String HEADER = "userId,username,passwordHash,role,createdAt";

    private final Path filePath;

    public CsvFileUserRepository(@Value("${finance.storage.users-file}") String filePath) {
        this.filePath = Path.of(filePath);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return readAll().stream()
                .filter(u -> u.username().equals(username))
                .findFirst();
    }

    @Override
    public void save(User user) {
        var users = readAll();
        users.removeIf(u -> u.userId().equals(user.userId()));
        users.add(user);
        writeAll(users);
    }

    private List<User> readAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try {
            return Files.readAllLines(filePath).stream()
                    .filter(line -> !line.startsWith("#") && !line.startsWith("userId") && !line.isBlank())
                    .map(this::parseLine)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new RepositoryException("Failed to read users file: " + filePath, e);
        }
    }

    private void writeAll(List<User> users) {
        try {
            Files.createDirectories(filePath.getParent());
            var lines = new ArrayList<String>();
            lines.add(HEADER);
            users.stream().map(this::formatLine).forEach(lines::add);
            Files.write(filePath, lines);
        } catch (IOException e) {
            throw new RepositoryException("Failed to write users file: " + filePath, e);
        }
    }

    private User parseLine(String line) {
        var parts = line.split(",", 5);
        return new User(
                UserId.of(parts[0]),
                new Username(parts[1]),
                new HashedPassword(parts[2]),
                Role.valueOf(parts[3]),
                LocalDateTime.parse(parts[4])
        );
    }

    private String formatLine(User user) {
        return String.join(",",
                user.userId().toString(),
                user.username().value(),
                user.hashedPassword().value(),
                user.role().name(),
                user.createdAt().toString()
        );
    }
}
