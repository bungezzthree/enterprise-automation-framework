package com.enterprise.qa.android.steps;

import com.enterprise.qa.android.screens.CartScreen;
import com.enterprise.qa.android.screens.CheckoutScreen;
import com.enterprise.qa.android.screens.ProductsScreen;
import com.enterprise.qa.core.steps.mobile.MobileContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/** Catalog, cart and checkout steps of the Swag Labs sample app. */
public class ShoppingSteps {

    private final MobileContext mobile;

    public ShoppingSteps(MobileContext mobile) {
        this.mobile = mobile;
    }

    // ---------- catalog ----------

    @Then("I should see the product {string} priced {string}")
    public void productPriced(String product, String price) {
        assertThat(products().priceOf(product)).isEqualTo(price);
    }

    @When("I sort products by {string}")
    public void sortBy(String option) {
        products().sortBy(option);
    }

    @Then("the first product should be {string}")
    public void firstProduct(String expected) {
        assertThat(products().productNames().getFirst()).isEqualTo(expected);
    }

    // ---------- cart ----------

    @When("I add {string} to the cart")
    public void addToCart(String product) {
        products().addToCart(product);
    }

    @Then("the cart badge should show {string}")
    public void cartBadge(String expected) {
        assertThat(products().cartBadge()).isEqualTo(expected);
    }

    @When("I open the cart")
    public void openCart() {
        products().openCart();
    }

    @Then("the cart should contain {string}")
    public void cartContains(String product) {
        assertThat(cart().itemNames()).contains(product);
    }

    @When("I remove {string} from the cart")
    public void removeFromCart(String product) {
        cart().remove(product);
    }

    @Then("the cart should be empty")
    public void cartEmpty() {
        assertThat(cart().isEmpty()).as("cart has no items").isTrue();
    }

    // ---------- checkout ----------

    @When("I check out with first name {string}, last name {string} and postal code {string}")
    public void checkoutWith(String first, String last, String zip) {
        cart().checkout();
        checkout().enterInformation(first, last, zip);
    }

    @And("I finish the order")
    public void finishOrder() {
        checkout().finish();
    }

    @Then("I should see the order confirmation {string}")
    public void orderConfirmation(String expected) {
        assertThat(checkout().confirmationShown()).as("confirmation '%s' is shown", expected).isTrue();
    }

    @Then("I should see the checkout error {string}")
    public void checkoutError(String expected) {
        assertThat(checkout().errorMessage()).contains(expected);
    }

    private ProductsScreen products() {
        return new ProductsScreen(mobile.actions());
    }

    private CartScreen cart() {
        return new CartScreen(mobile.actions());
    }

    private CheckoutScreen checkout() {
        return new CheckoutScreen(mobile.actions());
    }
}
