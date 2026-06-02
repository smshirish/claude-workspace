package com.finance.app.domain.model;

public record Username(String value) {

    private static final int MAX_LENGTH = 50;

    public Username {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (value.trim().length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Username cannot exceed " + MAX_LENGTH + " characters");
        }
        value = value.trim().toLowerCase();
    }

    @Override
    public String toString() {
        return value;
    }
}
