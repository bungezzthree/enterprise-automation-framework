package com.enterprise.qa.android.screens;

import com.enterprise.qa.core.mobile.MobileActions;
import org.openqa.selenium.By;

import java.util.List;

import static com.enterprise.qa.core.mobile.Locators.acc;
import static com.enterprise.qa.core.mobile.Locators.text;
import static com.enterprise.qa.core.mobile.Locators.xpath;

public class CartScreen {

    private static final By HEADER = text("YOUR CART");
    private static final By ITEM_TITLES = xpath("//android.view.ViewGroup[@content-desc='test-Item']//android.view.ViewGroup[@content-desc='test-Description']/android.widget.TextView[1]");
    private static final By CHECKOUT = acc("test-CHECKOUT");

    private final MobileActions ui;

    public CartScreen(MobileActions ui) {
        this.ui = ui;
    }

    public boolean isDisplayed() {
        return ui.isDisplayed(HEADER);
    }

    public List<String> itemNames() {
        return ui.texts(ITEM_TITLES);
    }

    public void remove(String product) {
        ui.tap(xpath("//android.view.ViewGroup[@content-desc='test-Item'][.//android.widget.TextView[@text='" + product
                + "']]//android.view.ViewGroup[@content-desc='test-REMOVE']"));
    }

    public boolean isEmpty() {
        return ui.isGone(ITEM_TITLES);
    }

    public void checkout() {
        ui.scrollToAccessibilityId("test-CHECKOUT");
        ui.tap(CHECKOUT);
    }
}
