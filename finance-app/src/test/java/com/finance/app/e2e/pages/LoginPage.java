package com.finance.app.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage {

    private static final By USERNAME_INPUT = By.id("username");
    private static final By PASSWORD_INPUT = By.id("password");
    private static final By SUBMIT_BUTTON  = By.cssSelector("button[type='submit']");
    private static final By ERROR_ALERT    = By.cssSelector(".alert-error");

    private final WebDriver driver;

    public LoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public void open(String baseUrl) {
        driver.get(baseUrl + "/login");
    }

    public void loginAs(String username, String password) {
        driver.findElement(USERNAME_INPUT).sendKeys(username);
        driver.findElement(PASSWORD_INPUT).sendKeys(password);
        driver.findElement(SUBMIT_BUTTON).click();
    }

    public boolean isErrorDisplayed() {
        return !driver.findElements(ERROR_ALERT).isEmpty()
                && driver.findElement(ERROR_ALERT).isDisplayed();
    }

    public String getErrorText() {
        return driver.findElement(ERROR_ALERT).getText();
    }
}
