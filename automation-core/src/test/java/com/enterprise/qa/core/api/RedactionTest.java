package com.enterprise.qa.core.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionTest {

    @Test
    void masksSecretFieldsInJson() {
        String masked = Redaction.mask("{\"user\":\"ada\",\"password\":\"s3cret\",\"access_token\":\"abc.def\"}");
        assertThat(masked).isEqualTo("{\"user\":\"ada\",\"password\":\"***\",\"access_token\":\"***\"}");
    }

    @Test
    void masksBearerTokens() {
        assertThat(Redaction.mask("Authorization: Bearer tdk_abc123.XYZ")).isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void leavesOrdinaryTextAndNullAlone() {
        assertThat(Redaction.mask("{\"title\":\"hello\"}")).isEqualTo("{\"title\":\"hello\"}");
        assertThat(Redaction.mask(null)).isNull();
    }

    @Test
    void describesARequestWithQueryAndMaskedBody() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("_page", "2");
        query.put("_limit", "5");
        assertThat(Redaction.describeRequest("get", "/posts", query, null)).isEqualTo("GET /posts?_page=2&_limit=5");
        assertThat(Redaction.describeRequest("POST", "/login", Map.of(), "{\"password\":\"x\"}")).isEqualTo("POST /login\n{\"password\":\"***\"}");
    }
}
