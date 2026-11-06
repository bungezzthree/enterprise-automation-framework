package com.enterprise.qa.android.screens;

import com.enterprise.qa.core.mobile.Locators;
import com.enterprise.qa.core.mobile.MobileActions;
import org.openqa.selenium.By;

import java.util.List;

import static com.enterprise.qa.core.mobile.Locators.acc;
import static com.enterprise.qa.core.mobile.Locators.xpath;

/** The product list with the sort selector and the cart icon. */
public class ProductsScreen {

    private static final By TITLE = acc("test-PRODUCTS");
    private static final By ITEM_TITLES = xpath("//android.widget.TextView[@content-desc='test-Item title']");
    private static final By SORT_BUTTON = acc("test-Modal Selector Button");
    private static final By CART = acc("test-Cart");
    private static final By CART_BADGE = xpath("//android.view.ViewGroup[@content-desc='test-Cart']//android.widget.TextView");
    private static final By MENU = acc("test-Menu");

    private final MobileActions ui;

    public ProductsScreen(MobileActions ui) {
        this.ui = ui;
    }

    public boolean isDisplayed() {
        return ui.isDisplayed(TITLE);
    }

    /** XPath of one product row (the "test-Item" container), found by the title it contains. */
    private static String row(String product) {
        return "//android.view.ViewGroup[@content-desc='test-Item'][.//android.widget.TextView[@text='" + product + "']]";
    }

    public String priceOf(String product) {
        ui.scrollToText(product);
        return ui.text(xpath(row(product) + "//android.widget.TextView[@content-desc='test-Price']"));
    }

    public List<String> productNames() {
        return ui.texts(ITEM_TITLES);
    }

    public void sortBy(String option) {
        ui.tap(SORT_BUTTON);
        ui.tap(Locators.text(option));
    }

    public void addToCart(String product) {
        ui.scrollToText(product);
        ui.tap(xpath(row(product) + "//android.view.ViewGroup[@content-desc='test-ADD TO CART']"));
    }

    /** The number on the cart icon, or an empty string when the cart is empty (no badge is drawn). */
    public String cartBadge() {
        return ui.isDisplayed(CART_BADGE) ? ui.text(CART_BADGE) : "";
    }

    public void openCart() {
        ui.tap(CART);
    }

    public void openMenu() {
        ui.tap(MENU);
    }
}
