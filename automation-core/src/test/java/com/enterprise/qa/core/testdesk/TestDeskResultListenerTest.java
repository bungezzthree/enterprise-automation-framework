package com.enterprise.qa.core.testdesk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDeskResultListenerTest {

    @Test
    void evidenceFileNameAddsAnExtensionFromTheMediaType() {
        assertThat(TestDeskResultListener.evidenceFileName("failure-screenshot", "image/png")).isEqualTo("failure-screenshot.png");
        assertThat(TestDeskResultListener.evidenceFileName("api-exchange", "text/plain; charset=utf-8")).isEqualTo("api-exchange.txt");
        assertThat(TestDeskResultListener.evidenceFileName("trace", "application/zip")).isEqualTo("trace.bin");
    }

    @Test
    void evidenceFileNameDoesNotDoubleTheExtensionAndCleansUnsafeCharacters() {
        assertThat(TestDeskResultListener.evidenceFileName("shot.PNG", "image/png")).isEqualTo("shot.PNG");
        assertThat(TestDeskResultListener.evidenceFileName("a/b:c", "image/png")).isEqualTo("a_b_c.png");
    }

    @Test
    void anUnnamedAttachmentIsCalledEvidence() {
        assertThat(TestDeskResultListener.evidenceFileName(null, "image/jpeg")).isEqualTo("evidence.jpg");
        assertThat(TestDeskResultListener.evidenceFileName("  ", null)).isEqualTo("evidence.bin");
    }

    @Test
    void errorMessageNamesTheExceptionAndIsBounded() {
        assertThat(TestDeskResultListener.errorMessage(null)).isNull();
        assertThat(TestDeskResultListener.errorMessage(new AssertionError("expected 200 but was 404")))
                .isEqualTo("AssertionError: expected 200 but was 404");
        assertThat(TestDeskResultListener.errorMessage(new RuntimeException("x".repeat(5000)))).hasSize(2000);
    }
}
