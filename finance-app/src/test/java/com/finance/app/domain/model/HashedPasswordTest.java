package com.finance.app.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashedPasswordTest {

    @Test
    void constructor_withValidHash_storesValue() {
        var hash = new HashedPassword("$2a$10$somehashvalue");
        assertThat(hash.value()).isEqualTo("$2a$10$somehashvalue");
    }

    @Test
    void constructor_withNull_throwsException() {
        assertThatThrownBy(() -> new HashedPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_withBlank_throwsException() {
        assertThatThrownBy(() -> new HashedPassword("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
