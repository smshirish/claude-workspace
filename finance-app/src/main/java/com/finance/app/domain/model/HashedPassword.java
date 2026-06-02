package com.finance.app.domain.model;

public record HashedPassword(String value) {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HashedPassword cannot be blank");
        }
    }
}
