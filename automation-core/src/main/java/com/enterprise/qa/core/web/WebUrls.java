package com.enterprise.qa.core.web;

final class WebUrls {

    private WebUrls() {
    }

    static String resolve(String baseUrl, String pathOrUrl) {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || baseUrl == null) {
            return pathOrUrl;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
    }
}
