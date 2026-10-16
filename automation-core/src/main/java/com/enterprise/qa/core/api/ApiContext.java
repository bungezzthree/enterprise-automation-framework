package com.enterprise.qa.core.api;

import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-scenario API state, shared between step classes by Cucumber's PicoContainer. */
public class ApiContext {

    private final ApiClient client = new ApiClient();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    private Response response;

    public ApiClient client() {
        return client;
    }

    public Map<String, String> queryParams() {
        return queryParams;
    }

    public Response response() {
        if (response == null) {
            throw new IllegalStateException("No request has been sent in this scenario yet");
        }
        return response;
    }

    public void response(Response response) {
        this.response = response;
        this.queryParams.clear();
    }
}
