package com.enterprise.qa.core.testlink;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal TestLink XML-RPC client (TestLink 1.9.x, endpoint {@code lib/api/xmlrpc/v1/xmlrpc.php}).
 * Only the calls the framework needs, with no extra dependency.
 */
public class TestLinkClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI endpoint;
    private final String devKey;

    public TestLinkClient(String url, String devKey) {
        this.endpoint = URI.create(url);
        this.devKey = devKey;
    }

    /**
     * Reports an execution result.
     *
     * @param status TestLink status code: {@code p} passed, {@code f} failed, {@code b} blocked
     */
    public String reportTestCaseResult(String testPlanId, String buildName, String externalId,
                                       String status, String notes) throws IOException, InterruptedException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("devKey", devKey);
        params.put("testplanid", Integer.parseInt(testPlanId));
        params.put("buildname", buildName);
        params.put("testcaseexternalid", externalId);
        params.put("status", status);
        params.put("notes", notes == null ? "" : notes);
        params.put("guess", true);
        return call("tl.reportTCResult", params);
    }

    public String ping() throws IOException, InterruptedException {
        return call("tl.sayHello", Map.of());
    }

    private String call(String method, Map<String, Object> params) throws IOException, InterruptedException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><methodCall><methodName>")
                .append(method).append("</methodName><params><param><value><struct>");
        params.forEach((name, value) -> xml.append("<member><name>").append(name).append("</name><value>")
                .append(encode(value)).append("</value></member>"));
        xml.append("</struct></value></param></params></methodCall>");

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xml.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().contains("<fault>")) {
            throw new IOException("TestLink " + method + " failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        return response.body();
    }

    private static String encode(Object value) {
        if (value instanceof Integer i) {
            return "<int>" + i + "</int>";
        }
        if (value instanceof Boolean b) {
            return "<boolean>" + (b ? 1 : 0) + "</boolean>";
        }
        String s = String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<string>" + s + "</string>";
    }
}
