package com.enterprise.qa.android.screens;

import com.enterprise.qa.core.mobile.MobileActions;
import org.openqa.selenium.By;

import static com.enterprise.qa.core.mobile.Locators.acc;
import static com.enterprise.qa.core.mobile.Locators.textContains;
import static com.enterprise.qa.core.mobile.Locators.xpath;

/** The three checkout pages: your info, overview, complete. */
public class CheckoutScreen {

    private static final By FIRST_NAME = acc("test-First Name");
    private static final By LAST_NAME = acc("test-Last Name");
    private static final By POSTAL_CODE = acc("test-Zip/Postal Code");
    private static final By CONTINUE = acc("test-CONTINUE");
    private static final By ERROR_TEXT = xpath("//*[@content-desc='test-Error message']//android.widget.TextView");
    private static final By FINISH = acc("test-FINISH");
    private static final By CONFIRMATION = textContains("THANK YOU FOR YOU ORDER");

    private final MobileActions ui;

    public CheckoutScreen(MobileActions ui) {
        this.ui = ui;
    }

    /** Fills the "your info" page and continues; empty values are left untouched. */
    public void enterInformation(String firstName, String lastName, String postalCode) {
        if (!firstName.isEmpty()) {
            ui.type(FIRST_NAME, firstName);
        }
        if (!lastName.isEmpty()) {
            ui.type(LAST_NAME, lastName);
        }
        if (!postalCode.isEmpty()) {
            ui.type(POSTAL_CODE, postalCode);
        }
        ui.hideKeyboardIfShown();
        ui.tap(CONTINUE);
    }

    public String errorMessage() {
        return ui.text(ERROR_TEXT);
    }

    public void finish() {
        ui.scrollToAccessibilityId("test-FINISH");
        ui.tap(FINISH);
    }

    public boolean confirmationShown() {
        return ui.isDisplayed(CONFIRMATION);
    }
}
