package com.enterprise.qa.core.api;

import java.util.Map;
import java.util.regex.Pattern;

/** Masks secrets before request/response text is stored as test evidence. */
public final class Redaction {

    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"(?:password|passwd|pwd|token|access_token|refresh_token|secret|client_secret|api_key|apikey|authorization)\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private Redaction() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = JSON_SECRET.matcher(text).replaceAll("$1***$2");
        return BEARER.matcher(masked).replaceAll("$1***");
    }

    /** One readable description of a request: {@code GET /posts?_page=2} plus the (masked) body, if any. */
    public static String describeRequest(String method, String path, Map<String, String> query, String body) {
        StringBuilder out = new StringBuilder(method.toUpperCase()).append(' ').append(path);
        if (query != null && !query.isEmpty()) {
            out.append('?');
            query.forEach((k, v) -> out.append(k).append('=').append(v).append('&'));
            out.setLength(out.length() - 1);
        }
        if (body != null && !body.isBlank()) {
            out.append('\n').append(mask(body.strip()));
        }
        return out.toString();
    }
}
