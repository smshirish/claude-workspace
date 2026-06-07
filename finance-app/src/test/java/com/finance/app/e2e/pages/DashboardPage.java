package com.finance.app.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class DashboardPage {

    private static final By HEADING = By.cssSelector("main h2");

    private final WebDriver driver;

    public DashboardPage(WebDriver driver) {
        this.driver = driver;
    }

    public boolean isOnDashboard() {
        return driver.getCurrentUrl().endsWith("/dashboard");
    }

    public String getHeadingText() {
        return driver.findElement(HEADING).getText();
    }
}
