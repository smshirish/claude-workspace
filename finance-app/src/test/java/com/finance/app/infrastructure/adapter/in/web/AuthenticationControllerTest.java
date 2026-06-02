package com.finance.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationControllerTest {

    private final AuthenticationController controller = new AuthenticationController();

    @Test
    void loginPage_returnsLoginViewName() {
        assertThat(controller.loginPage()).isEqualTo("login");
    }
}
