package com.enterprise.qa.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Layered configuration. Lookup order (first hit wins):
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dbrowser=firefox}</li>
 *   <li>Environment variable, upper-snake form, e.g. {@code BROWSER}, {@code API_BASE_URL}</li>
 *   <li>{@code config/<env>.properties} on the classpath (env defaults to {@code local})</li>
 *   <li>{@code config/default.properties} on the classpath</li>
 * </ol>
 */
public final class Config {

    private static final Properties FILE_PROPS = load();

    private Config() {
    }

    public static String env() {
        String env = System.getProperty("env");
        if (isBlank(env)) {
            env = System.getenv("TEST_ENV");
        }
        return isBlank(env) ? "local" : env;
    }

    public static String get(String key) {
        String value = System.getProperty(key);
        if (isBlank(value)) {
            value = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        }
        if (isBlank(value)) {
            value = FILE_PROPS.getProperty(key);
        }
        return isBlank(value) ? null : value.trim();
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required configuration '" + key + "' for env '" + env() + "'");
        }
        return value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static Properties load() {
        Properties props = new Properties();
        loadResource(props, "config/default.properties");
        loadResource(props, "config/" + env() + ".properties");
        return props;
    }

    private static void loadResource(Properties props, String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
