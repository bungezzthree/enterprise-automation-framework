package com.enterprise.qa.android.screens;

import com.enterprise.qa.core.mobile.MobileActions;
import org.openqa.selenium.By;

import static com.enterprise.qa.core.mobile.Locators.acc;
import static com.enterprise.qa.core.mobile.Locators.xpath;

/** Login screen of the Swag Labs sample app. Its accessibility ids are "test-" plus the field name. */
public class LoginScreen {

    private static final By USERNAME = acc("test-Username");
    private static final By PASSWORD = acc("test-Password");
    private static final By LOGIN_BUTTON = acc("test-LOGIN");
    private static final By ERROR_TEXT = xpath("//*[@content-desc='test-Error message']//android.widget.TextView");

    private final MobileActions ui;

    public LoginScreen(MobileActions ui) {
        this.ui = ui;
    }

    public boolean isDisplayed() {
        return ui.isDisplayed(USERNAME);
    }

    public void loginAs(String username, String password) {
        ui.type(USERNAME, username);
        ui.type(PASSWORD, password);
        ui.hideKeyboardIfShown();
        ui.tap(LOGIN_BUTTON);
    }

    public String errorMessage() {
        return ui.text(ERROR_TEXT);
    }
}
