package com.enterprise.qa.web.pages;

import com.enterprise.qa.core.web.UiDriver;

public class CheckoutPage {

    private static final String FIRST_NAME = "[data-test='firstName']";
    private static final String LAST_NAME = "[data-test='lastName']";
    private static final String POSTAL_CODE = "[data-test='postalCode']";
    private static final String CONTINUE = "[data-test='continue']";
    private static final String FINISH = "[data-test='finish']";
    private static final String ERROR = "[data-test='error']";
    private static final String COMPLETE_HEADER = "[data-test='complete-header']";
    private static final String TOTAL = "[data-test='total-label']";

    private final UiDriver ui;

    public CheckoutPage(UiDriver ui) {
        this.ui = ui;
    }

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
        ui.click(CONTINUE);
    }

    public String totalLabel() {
        return ui.text(TOTAL);
    }

    public void finish() {
        ui.click(FINISH);
    }

    public String confirmationHeader() {
        return ui.text(COMPLETE_HEADER);
    }

    public String errorMessage() {
        return ui.isVisible(ERROR) ? ui.text(ERROR) : "";
    }
}
