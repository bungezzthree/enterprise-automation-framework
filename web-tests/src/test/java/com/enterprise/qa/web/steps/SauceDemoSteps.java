package com.enterprise.qa.web.steps;

import com.enterprise.qa.core.config.Config;
import com.enterprise.qa.core.steps.web.WebContext;
import com.enterprise.qa.web.pages.CartPage;
import com.enterprise.qa.web.pages.CheckoutPage;
import com.enterprise.qa.web.pages.InventoryPage;
import com.enterprise.qa.web.pages.LoginPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SauceDemoSteps {

    private final WebContext web;

    public SauceDemoSteps(WebContext web) {
        this.web = web;
    }

    // ---------- login ----------

    @Given("I am on the login page")
    public void onLoginPage() {
        login().open();
    }

    @When("I log in as the {word} user")
    public void loginAsConfiguredUser(String kind) {
        login().loginAs(Config.require("web.user." + kind), Config.require("web.user.password"));
    }

    @When("I log in with username {string} and password {string}")
    public void loginWith(String username, String password) {
        login().loginAs(username, password);
    }

    @Given("I am logged in as the standard user")
    public void loggedInAsStandardUser() {
        onLoginPage();
        loginAsConfiguredUser("standard");
        assertThat(inventory().isDisplayed()).as("inventory page after login").isTrue();
    }

    @Then("I should land on the products page")
    public void onProductsPage() {
        assertThat(inventory().isDisplayed()).as("products page displayed").isTrue();
    }

    @Then("I should see the login error {string}")
    public void loginError(String expected) {
        assertThat(login().errorMessage()).isEqualTo(expected);
    }

    // ---------- catalog ----------

    @Then("I should see {int} products")
    public void productCount(int expected) {
        assertThat(inventory().productCount()).isEqualTo(expected);
    }

    @Then("every product should have a name and a price")
    public void productsHaveNameAndPrice() {
        assertThat(inventory().productNames()).allSatisfy(n -> assertThat(n).isNotBlank());
        assertThat(inventory().productPrices()).allSatisfy(p -> assertThat(p).isPositive());
    }

    @When("I sort products by {string}")
    public void sortBy(String option) {
        inventory().sortBy(option);
    }

    @Then("product prices should be in ascending order")
    public void pricesAscending() {
        List<Double> prices = inventory().productPrices();
        assertThat(prices).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @And("the first product should be {string}")
    public void firstProduct(String expected) {
        assertThat(inventory().productNames().getFirst()).isEqualTo(expected);
    }

    // ---------- cart ----------

    @When("I add {string} to the cart")
    public void addToCart(String product) {
        inventory().addToCart(product);
    }

    @When("I remove {string} from the cart")
    public void removeFromCart(String product) {
        inventory().removeFromCart(product);
    }

    @Then("the cart badge should show {string}")
    public void cartBadge(String expected) {
        assertThat(inventory().cartBadge()).isEqualTo(expected);
    }

    @Then("the cart badge should not be displayed")
    public void cartBadgeHidden() {
        assertThat(inventory().cartBadgeGone()).as("cart badge hidden").isTrue();
    }

    @When("I open the cart")
    public void openCart() {
        inventory().openCart();
    }

    @Then("the cart should contain {string}")
    public void cartContains(String product) {
        assertThat(cart().itemNames()).contains(product);
    }

    // ---------- checkout ----------

    @When("I check out with first name {string}, last name {string} and postal code {string}")
    public void checkoutWith(String first, String last, String zip) {
        cart().checkout();
        checkout().enterInformation(first, last, zip);
    }

    @And("I finish the order")
    public void finishOrder() {
        assertThat(checkout().totalLabel()).startsWith("Total: $");
        checkout().finish();
    }

    @Then("I should see the order confirmation {string}")
    public void orderConfirmation(String expected) {
        assertThat(checkout().confirmationHeader()).isEqualTo(expected);
    }

    @Then("I should see the checkout error {string}")
    public void checkoutError(String expected) {
        assertThat(checkout().errorMessage()).isEqualTo(expected);
    }

    private LoginPage login() {
        return new LoginPage(web.driver());
    }

    private InventoryPage inventory() {
        return new InventoryPage(web.driver());
    }

    private CartPage cart() {
        return new CartPage(web.driver());
    }

    private CheckoutPage checkout() {
        return new CheckoutPage(web.driver());
    }
}
