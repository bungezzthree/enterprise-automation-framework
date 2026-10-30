package com.enterprise.qa.core.testdesk;

import com.enterprise.qa.core.testdesk.TestDeskClient.TestDeskException;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDeskClientTest {

    record Received(String method, String path, String auth, String contentType, String body) {
    }

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile String answer = "{\"ok\":true}";
    private TestDeskClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Content-Type"), body));
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new TestDeskClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "tdk_secret",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 0);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void syncUsesPutWithBearerTokenAndJson() throws Exception {
        answer = "{\"created\":0,\"linked\":1,\"errors\":[]}";
        JsonObject result = client.syncCases("API", new JsonObject());
        assertThat(result.get("linked").getAsInt()).isEqualTo(1);
        Received r = received.get(0);
        assertThat(r.method()).isEqualTo("PUT");
        assertThat(r.path()).isEqualTo("/api/projects/API/automation/cases");
        assertThat(r.auth()).isEqualTo("Bearer tdk_secret");
        assertThat(r.contentType()).startsWith("application/json");
    }

    @Test
    void resultsUsePostAndReturnThePlanId() throws Exception {
        answer = "{\"plan_id\":7,\"recorded\":3}";
        JsonObject result = client.recordResults("WEB", new JsonObject());
        assertThat(result.get("plan_id").getAsLong()).isEqualTo(7);
        assertThat(received.get(0).method()).isEqualTo("POST");
        assertThat(received.get(0).path()).isEqualTo("/api/projects/WEB/automation/results");
    }

    @Test
    void evidenceIsSentAsMultipartWithCaseKeyRunIdAndFile(@TempDir Path dir) throws Exception {
        Path shot = Files.writeString(dir.resolve("shot.txt"), "PIXELS");
        answer = "{\"ok\":true,\"files\":1}";
        client.uploadEvidence(7, "case_key", "API-012", "run-1", List.of(new ScenarioRecord.Evidence("failure-screenshot.png", "image/png", shot)));
        Received r = received.get(0);
        assertThat(r.path()).isEqualTo("/api/plans/7/automation/evidence");
        assertThat(r.contentType()).startsWith("multipart/form-data; boundary=");
        assertThat(r.body()).contains("name=\"case_key\"\r\n\r\nAPI-012")
                .contains("name=\"run_id\"\r\n\r\nrun-1")
                .contains("name=\"evidence\"; filename=\"failure-screenshot.png\"")
                .contains("Content-Type: image/png")
                .contains("PIXELS");
    }

    @Test
    void clientErrorsAreNotRetriedAndCarryTheServerMessage() {
        status.set(401);
        answer = "{\"error\":\"Invalid or expired token\"}";
        assertThatThrownBy(() -> client.recordResults("API", new JsonObject()))
                .isInstanceOfSatisfying(TestDeskException.class, e -> {
                    assertThat(e.status()).isEqualTo(401);
                    assertThat(e.getMessage()).contains("401").contains("Invalid or expired token");
                });
        assertThat(received).hasSize(1);
    }

    @Test
    void serverErrorsAreRetriedOnce() {
        status.set(500);
        answer = "{\"error\":\"boom\"}";
        assertThatThrownBy(() -> client.syncCases("API", new JsonObject()))
                .isInstanceOfSatisfying(TestDeskException.class, e -> assertThat(e.status()).isEqualTo(500));
        assertThat(received).hasSize(2);
    }

    @Test
    void evidenceUploadIsNeverRetried(@TempDir Path dir) throws Exception {
        Path f = Files.writeString(dir.resolve("a.txt"), "x");
        status.set(503);
        assertThatThrownBy(() -> client.uploadEvidence(1, "case_key", "API-1", null, List.of(new ScenarioRecord.Evidence("a.txt", "text/plain", f))))
                .isInstanceOf(TestDeskException.class);
        assertThat(received).hasSize(1);
    }

    @Test
    void unreachableServerGivesAClearMessage() {
        TestDeskClient dead = new TestDeskClient("http://127.0.0.1:1", "t", Duration.ofMillis(500), Duration.ofSeconds(1), 0);
        assertThatThrownBy(() -> dead.syncCases("API", new JsonObject()))
                .isInstanceOfSatisfying(TestDeskException.class, e -> {
                    assertThat(e.status()).isZero();
                    assertThat(e.getMessage()).contains("Cannot reach TestDesk").doesNotContain("null");
                });
    }
}
