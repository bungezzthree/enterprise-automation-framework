package com.enterprise.qa.android.screens;

import com.enterprise.qa.core.mobile.MobileActions;
import org.openqa.selenium.By;

import static com.enterprise.qa.core.mobile.Locators.acc;

/** The side menu that opens from the burger icon on the products screen. */
public class MenuScreen {

    private static final By LOGOUT = acc("test-LOGOUT");

    private final MobileActions ui;

    public MenuScreen(MobileActions ui) {
        this.ui = ui;
    }

    public void logout() {
        ui.tap(LOGOUT);
    }
}
