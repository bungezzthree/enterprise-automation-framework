package com.enterprise.qa.web.pages;

import com.enterprise.qa.core.web.UiDriver;

public class LoginPage {

    private static final String USERNAME = "[data-test='username']";
    private static final String PASSWORD = "[data-test='password']";
    private static final String LOGIN_BUTTON = "[data-test='login-button']";
    private static final String ERROR = "[data-test='error']";

    private final UiDriver ui;

    public LoginPage(UiDriver ui) {
        this.ui = ui;
    }

    public LoginPage open() {
        ui.open("/");
        return this;
    }

    public void loginAs(String username, String password) {
        ui.type(USERNAME, username);
        ui.type(PASSWORD, password);
        ui.click(LOGIN_BUTTON);
    }

    public String errorMessage() {
        return ui.isVisible(ERROR) ? ui.text(ERROR) : "";
    }
}
