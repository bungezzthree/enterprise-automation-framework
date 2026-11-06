package com.enterprise.qa.android.steps;

import com.enterprise.qa.android.screens.LoginScreen;
import com.enterprise.qa.android.screens.MenuScreen;
import com.enterprise.qa.android.screens.ProductsScreen;
import com.enterprise.qa.core.config.Config;
import com.enterprise.qa.core.steps.mobile.MobileContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final MobileContext mobile;

    public LoginSteps(MobileContext mobile) {
        this.mobile = mobile;
    }

    @When("I log in as the {word} user")
    public void loginAsConfiguredUser(String kind) {
        login().loginAs(Config.require("mobile.user." + kind), Config.require("mobile.user.password"));
    }

    @When("I log in with username {string} and password {string}")
    public void loginWith(String username, String password) {
        login().loginAs(username, password);
    }

    @Given("I am logged in as the standard user")
    public void loggedInAsStandardUser() {
        loginAsConfiguredUser("standard");
        assertThat(products().isDisplayed()).as("products screen after login").isTrue();
    }

    @When("I log out")
    public void logout() {
        products().openMenu();
        new MenuScreen(mobile.actions()).logout();
    }

    @Then("I should see the products screen")
    public void productsScreenShown() {
        assertThat(products().isDisplayed()).as("products screen is displayed").isTrue();
    }

    @Then("I should see the login screen")
    public void loginScreenShown() {
        assertThat(login().isDisplayed()).as("login screen is displayed").isTrue();
    }

    @Then("I should see the login error {string}")
    public void loginError(String expected) {
        assertThat(login().errorMessage()).contains(expected);
    }

    private LoginScreen login() {
        return new LoginScreen(mobile.actions());
    }

    private ProductsScreen products() {
        return new ProductsScreen(mobile.actions());
    }
}
