package com.finance.app.e2e.login;

import com.finance.app.e2e.config.SeleniumConfig;
import com.finance.app.e2e.pages.DashboardPage;
import com.finance.app.e2e.pages.LoginPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginE2ETest extends SeleniumConfig {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("finance.storage.users-file",
                () -> tempDir.resolve("users.csv").toString());
    }

    @LocalServerPort
    int port;

    LoginPage loginPage;
    DashboardPage dashboardPage;

    @BeforeEach
    void setUpPages() {
        loginPage = new LoginPage(driver);
        dashboardPage = new DashboardPage(driver);
    }

    @Test
    void loginWithValidCredentials_landOnDashboard() {
        loginPage.open("http://localhost:" + port);
        loginPage.loginAs("admin", "admin123");

        assertThat(dashboardPage.isOnDashboard()).isTrue();
        assertThat(dashboardPage.getHeadingText()).isEqualTo("Dashboard");
    }

    @Test
    void loginWithWrongPassword_staysOnLoginWithErrorMessage() {
        loginPage.open("http://localhost:" + port);
        loginPage.loginAs("admin", "wrongpassword");

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(loginPage.isErrorDisplayed()).isTrue();
        assertThat(loginPage.getErrorText()).contains("Invalid username or password");
    }
}
