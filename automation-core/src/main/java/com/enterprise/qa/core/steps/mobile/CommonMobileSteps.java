package com.enterprise.qa.core.steps.mobile;

import com.enterprise.qa.core.mobile.Locators;
import com.enterprise.qa.core.mobile.MobileSession;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Generic, app-independent mobile steps. Add {@code com.enterprise.qa.core.steps.mobile} to the glue to use them. */
public class CommonMobileSteps {

    private final MobileContext mobile;

    public CommonMobileSteps(MobileContext mobile) {
        this.mobile = mobile;
    }

    @Given("the app is launched")
    public void appIsLaunched() {
        mobile.driver(); // the hook already started the app; this makes the intent visible in the scenario
    }

    @When("I relaunch the app")
    public void relaunchApp() {
        MobileSession.relaunchApp();
    }

    @When("I send the app to the background for {int} seconds")
    public void backgroundApp(int seconds) {
        mobile.driver().executeScript("mobile: backgroundApp", Map.of("seconds", seconds));
    }

    @When("I press the back button")
    public void pressBack() {
        mobile.actions().back();
    }

    @When("I tap the text {string}")
    public void tapText(String text) {
        mobile.actions().tap(Locators.text(text));
    }

    @Then("I should see the text {string}")
    public void seeText(String text) {
        assertThat(mobile.actions().isDisplayed(Locators.textContains(text))).as("text '%s' is displayed", text).isTrue();
    }

    @Then("I should not see the text {string}")
    public void doNotSeeText(String text) {
        assertThat(mobile.actions().isGone(Locators.textContains(text))).as("text '%s' is gone", text).isTrue();
    }
}
