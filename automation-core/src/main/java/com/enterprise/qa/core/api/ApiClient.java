package com.enterprise.qa.core.api;

import com.enterprise.qa.core.config.Config;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Thin REST Assured wrapper. Base URL, timeouts and default headers come from config
 * ({@code api.base.url}, {@code api.timeout.ms}, {@code api.auth.token}).
 */
public class ApiClient {

    private final RequestSpecification baseSpec;

    public ApiClient() {
        int timeoutMs = Config.getInt("api.timeout.ms", 15_000);
        RestAssuredConfig config = RestAssured.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeoutMs)
                .setParam("http.socket.timeout", timeoutMs));
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(Config.require("api.base.url"))
                .setConfig(config)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON);
        String token = Config.get("api.auth.token");
        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        if (Config.getBool("api.log.all", false)) {
            builder.log(LogDetail.ALL);
        }
        this.baseSpec = builder.build();
    }

    public Response send(String method, String path, Map<String, ?> queryParams, String body) {
        RequestSpecification request = RestAssured.given().spec(baseSpec);
        if (queryParams != null && !queryParams.isEmpty()) {
            request.queryParams(queryParams);
        }
        if (body != null) {
            request.body(body);
        }
        return request.when().request(method.toUpperCase(), path).then().extract().response();
    }
}
