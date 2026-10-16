package com.enterprise.qa.web.pages;

import com.enterprise.qa.core.web.UiDriver;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryPage {

    private static final String TITLE = "[data-test='title']";
    private static final String ITEM = "[data-test='inventory-item']";
    private static final String ITEM_NAME = "[data-test='inventory-item-name']";
    private static final String ITEM_PRICE = "[data-test='inventory-item-price']";
    private static final String SORT = "[data-test='product-sort-container']";
    private static final String CART_BADGE = "[data-test='shopping-cart-badge']";
    private static final String CART_LINK = "[data-test='shopping-cart-link']";

    private static final Map<String, String> SORT_OPTIONS = Map.of(
            "name (a to z)", "az",
            "name (z to a)", "za",
            "price (low to high)", "lohi",
            "price (high to low)", "hilo");

    private final UiDriver ui;

    public InventoryPage(UiDriver ui) {
        this.ui = ui;
    }

    public boolean isDisplayed() {
        return ui.isVisible(TITLE) && "Products".equals(ui.text(TITLE)) && ui.currentUrl().contains("inventory");
    }

    public int productCount() {
        ui.isVisible(ITEM);
        return ui.count(ITEM);
    }

    public List<String> productNames() {
        return ui.texts(ITEM_NAME);
    }

    public List<Double> productPrices() {
        return ui.texts(ITEM_PRICE).stream().map(p -> Double.parseDouble(p.replace("$", ""))).toList();
    }

    public void sortBy(String visibleOption) {
        String value = SORT_OPTIONS.get(visibleOption.toLowerCase(Locale.ROOT));
        if (value == null) {
            throw new IllegalArgumentException("Unknown sort option '" + visibleOption + "', expected one of " + SORT_OPTIONS.keySet());
        }
        ui.selectByValue(SORT, value);
    }

    public void addToCart(String productName) {
        ui.click("[data-test='add-to-cart-" + slug(productName) + "']");
    }

    public void removeFromCart(String productName) {
        ui.click("[data-test='remove-" + slug(productName) + "']");
    }

    public String cartBadge() {
        return ui.isVisible(CART_BADGE) ? ui.text(CART_BADGE) : "";
    }

    public boolean cartBadgeGone() {
        return ui.waitUntilHidden(CART_BADGE);
    }

    public void openCart() {
        ui.click(CART_LINK);
    }

    /** Sauce Demo builds button test ids from the product name: "Sauce Labs Backpack" -> sauce-labs-backpack. */
    private static String slug(String productName) {
        return productName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.()]+", "-");
    }
}
