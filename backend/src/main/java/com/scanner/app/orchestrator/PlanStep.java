package com.scanner.app.orchestrator;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public record PlanStep(
        String key,
        String label,
        int order,
        Duration timeout,
        boolean retryable,
        Map<String, String> settings
) {
    public PlanStep {
        settings = settings == null ? Collections.emptyMap() : Collections.unmodifiableMap(settings);
    }

    public String setting(String key, String fallback) {
        return settings.getOrDefault(key, fallback);
    }

    public int intSetting(String key, int fallback) {
        try {
            return Integer.parseInt(settings.get(key));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public boolean boolSetting(String key, boolean fallback) {
        String value = settings.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
