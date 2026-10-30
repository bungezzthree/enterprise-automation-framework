package com.enterprise.qa.core.testdesk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Minimal client for the TestDesk automation API (see TestDesk docs/AUTOMATION.md). Authenticates with a
 * {@code tdk_...} API token. Idempotent calls (sync, results) are retried once on connection errors and 5xx.
 */
public class TestDeskClient {

    /** A non-2xx answer from TestDesk. {@code status} is 0 when no answer was received. */
    public static class TestDeskException extends IOException {
        private final int status;

        public TestDeskException(int status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final Duration requestTimeout;
    private final long retryDelayMs;

    public TestDeskClient(String baseUrl, String token) {
        this(baseUrl, token, Duration.ofSeconds(10), Duration.ofSeconds(60), 2000);
    }

    public TestDeskClient(String baseUrl, String token, Duration connectTimeout, Duration requestTimeout, long retryDelayMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.requestTimeout = requestTimeout;
        this.retryDelayMs = retryDelayMs;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /** {@code PUT /api/projects/{project}/automation/cases}: create/link/update test cases from code. */
    public JsonObject syncCases(String project, JsonObject body) throws IOException, InterruptedException {
        HttpRequest.Builder b = request("/api/projects/" + encode(project) + "/automation/cases")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        return send(b.build(), 2);
    }

    /** {@code POST /api/projects/{project}/automation/results}: record results in a plan. */
    public JsonObject recordResults(String project, JsonObject body) throws IOException, InterruptedException {
        HttpRequest.Builder b = request("/api/projects/" + encode(project) + "/automation/results")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        return send(b.build(), 2);
    }

    /**
     * {@code POST /api/plans/{planId}/automation/evidence}: attach files to the latest result of a test case.
     * Uses {@code case_key} (design decision D5). Not retried, to avoid duplicate attachments.
     */
    public JsonObject uploadEvidence(long planId, String caseKey, String runId, List<ScenarioRecord.Evidence> files)
            throws IOException, InterruptedException {
        String boundary = "----eaf" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "case_key", caseKey);
        if (runId != null) {
            field(out, boundary, "run_id", runId);
        }
        for (ScenarioRecord.Evidence f : files) {
            file(out, boundary, f);
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = request("/api/plans/" + planId + "/automation/evidence")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        return send(b.build(), 1);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
    }

    private JsonObject send(HttpRequest request, int attempts) throws IOException, InterruptedException {
        TestDeskException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parse(response.body());
                }
                last = new TestDeskException(status, "TestDesk answered HTTP " + status + ": " + errorMessage(response.body()), null);
                if (status < 500) {
                    throw last;
                }
            } catch (TestDeskException e) {
                if (e.status() != 0 && e.status() < 500) {
                    throw e;
                }
                last = e;
            } catch (IOException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                last = new TestDeskException(0, "Cannot reach TestDesk at " + baseUrl + " (" + reason + ")", e);
            }
            if (attempt < attempts && retryDelayMs > 0) {
                Thread.sleep(retryDelayMs);
            }
        }
        throw last;
    }

    private static JsonObject parse(String body) throws TestDeskException {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            return json == null ? new JsonObject() : json;
        } catch (JsonParseException e) {
            throw new TestDeskException(0, "TestDesk returned a non-JSON answer: " + abbreviate(body), e);
        }
    }

    private static String errorMessage(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (JsonParseException ignored) {
            // not JSON, fall through
        }
        return abbreviate(body);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void file(ByteArrayOutputStream out, String boundary, ScenarioRecord.Evidence f) throws IOException {
        String fileName = f.fileName().replace("\"", "'").replace("\r", "").replace("\n", "");
        String type = f.mediaType() == null || f.mediaType().isBlank() ? "application/octet-stream" : f.mediaType();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"evidence\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(Files.readAllBytes(f.file()));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
