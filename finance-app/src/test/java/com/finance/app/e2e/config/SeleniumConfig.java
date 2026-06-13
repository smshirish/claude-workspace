package com.finance.app.e2e.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

public abstract class SeleniumConfig {

    protected WebDriver driver;

    @BeforeEach
    void setUpDriver() {
        WebDriverManager.chromiumdriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");

        // Use Chromium binary when Chrome is not installed (e.g. dev containers / CI)
        String chromiumPath = findChromiumBinary();
        if (chromiumPath != null) {
            options.setBinary(chromiumPath);
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }

    private static String findChromiumBinary() {
        for (String candidate : new String[]{"/usr/bin/chromium", "/usr/bin/chromium-browser"}) {
            if (new java.io.File(candidate).exists()) {
                return candidate;
            }
        }
        return null;
    }

    @AfterEach
    void tearDownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }
}
