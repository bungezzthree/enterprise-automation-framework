package com.enterprise.qa.core.reporting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the project's full test-case inventory (manual and automated), normally exported from TestLink.
 * The catalog is what gives "automation coverage" a denominator.
 *
 * <pre>
 * tc_key,title,categories,automated,priority,testlink_id
 * API-001,Retrieve all posts,regression|sanity,true,high,EAF-1
 * </pre>
 */
public final class TestCaseCatalog {

    private TestCaseCatalog() {
    }

    public static List<TestCaseInfo> load(String classpathResource) throws IOException {
        List<TestCaseInfo> cases = new ArrayList<>();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                return cases;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> cols = splitCsv(line);
                Set<String> categories = Stream.of(col(cols, 2).split("\\|"))
                        .map(c -> c.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", ""))
                        .collect(Collectors.toSet());
                cases.add(new TestCaseInfo(
                        col(cols, 0),
                        col(cols, 1),
                        categories.contains("regression"),
                        categories.contains("sanity"),
                        categories.contains("newfeature"),
                        Boolean.parseBoolean(col(cols, 3)),
                        emptyToNull(col(cols, 4)),
                        emptyToNull(col(cols, 5))));
            }
        }
        return cases;
    }

    private static String col(List<String> cols, int i) {
        return i < cols.size() ? cols.get(i).trim() : "";
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    /** Minimal RFC 4180 split: supports quoted fields containing commas and doubled quotes. */
    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
