package com.enterprise.qa.core.steps.api;

import com.enterprise.qa.core.api.ApiContext;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic, reusable API step vocabulary. Add {@code com.enterprise.qa.core.steps.api} to an
 * API project's glue to use it. Contracts are JSON Schemas under {@code contracts/} on the classpath.
 */
public class CommonApiSteps {

    private final ApiContext api;

    public CommonApiSteps(ApiContext api) {
        this.api = api;
    }

    @Given("the query parameter {string} is {string}")
    public void queryParameter(String name, String value) {
        api.queryParams().put(name, value);
    }

    @When("I send a {word} request to {string}")
    public void sendRequest(String method, String path) {
        api.response(api.client().send(method, path, api.queryParams(), null));
    }

    @When("I send a {word} request to {string} with body:")
    public void sendRequestWithBody(String method, String path, String body) {
        api.response(api.client().send(method, path, api.queryParams(), body));
    }

    @Then("the response status code should be {int}")
    public void statusCode(int expected) {
        assertThat(api.response().statusCode())
                .as("HTTP status, body: %s", abbreviate(api.response().asString()))
                .isEqualTo(expected);
    }

    @Then("the response should match the {string} contract")
    public void matchesContract(String contract) {
        api.response().then().assertThat()
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("contracts/" + contract + ".json"));
    }

    @Then("the response should be a list of {int} items")
    public void listSize(int expected) {
        List<Object> items = api.response().jsonPath().getList("$");
        assertThat(items).hasSize(expected);
    }

    @Then("the response should be a non-empty list")
    public void nonEmptyList() {
        List<Object> items = api.response().jsonPath().getList("$");
        assertThat(items).isNotEmpty();
    }

    @Then("the response field {string} should be {string}")
    public void fieldEquals(String path, String expected) {
        Object actual = api.response().jsonPath().get(path);
        assertThat(String.valueOf(actual)).as("field '%s'", path).isEqualTo(expected);
    }

    @Then("every item field {string} should be {string}")
    public void everyItemFieldEquals(String field, String expected) {
        List<Object> values = api.response().jsonPath().getList(field);
        assertThat(values).as("values of '%s'", field).isNotEmpty()
                .allSatisfy(v -> assertThat(String.valueOf(v)).isEqualTo(expected));
    }

    @Then("the response body should be {string}")
    public void bodyEquals(String expected) {
        assertThat(api.response().asString().replaceAll("\\s", "")).isEqualTo(expected.replaceAll("\\s", ""));
    }

    @Then("the response header {string} should contain {string}")
    public void headerContains(String header, String expected) {
        assertThat(api.response().header(header)).as("header '%s'", header).contains(expected);
    }

    @Then("the response time should be below {int} ms")
    public void responseTime(int maxMs) {
        assertThat(api.response().time()).as("response time (ms)").isLessThan(maxMs);
    }

    @After
    public void attachLastResponse(Scenario scenario) {
        if (scenario.isFailed()) {
            try {
                Response r = api.response();
                String dump = "HTTP " + r.statusCode() + "\n" + r.headers() + "\n\n" + r.asPrettyString();
                scenario.attach(dump.getBytes(StandardCharsets.UTF_8), "text/plain", "last-response");
            } catch (IllegalStateException ignored) {
                // failed before any request was sent
            }
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
