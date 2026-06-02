package com.finance.app.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UsernameTest {

    @Test
    void constructor_normalisesToLowercase() {
        assertThat(new Username("Alice").value()).isEqualTo("alice");
    }

    @Test
    void constructor_trimsSurroundingWhitespace() {
        assertThat(new Username("  bob  ").value()).isEqualTo("bob");
    }

    @Test
    void equals_isCaseInsensitive() {
        assertThat(new Username("alice")).isEqualTo(new Username("ALICE"));
    }

    @Test
    void constructor_withNull_throwsException() {
        assertThatThrownBy(() -> new Username(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_withBlank_throwsException() {
        assertThatThrownBy(() -> new Username("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_withTooLongName_throwsException() {
        assertThatThrownBy(() -> new Username("a".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_withExactlyMaxLength_succeeds() {
        assertThatCode(() -> new Username("a".repeat(50)))
                .doesNotThrowAnyException();
    }
}
