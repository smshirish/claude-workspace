package com.finance.app.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserIdTest {

    @Test
    void generate_producesUniqueIds() {
        assertThat(UserId.generate()).isNotEqualTo(UserId.generate());
    }

    @Test
    void of_withValidUuidString_createsUserId() {
        var uuid = UUID.randomUUID();
        var userId = UserId.of(uuid.toString());
        assertThat(userId.value()).isEqualTo(uuid);
    }

    @Test
    void of_withInvalidString_throwsException() {
        assertThatThrownBy(() -> UserId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_withNull_throwsException() {
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_returnsUuidString() {
        var uuid = UUID.randomUUID();
        assertThat(new UserId(uuid).toString()).isEqualTo(uuid.toString());
    }
}
