package com.enterprise.qa.web.pages;

import com.enterprise.qa.core.web.UiDriver;

import java.util.List;

public class CartPage {

    private static final String ITEM_NAME = "[data-test='inventory-item-name']";
    private static final String CHECKOUT = "[data-test='checkout']";

    private final UiDriver ui;

    public CartPage(UiDriver ui) {
        this.ui = ui;
    }

    public List<String> itemNames() {
        return ui.texts(ITEM_NAME);
    }

    public void checkout() {
        ui.click(CHECKOUT);
    }
}
