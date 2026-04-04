package com.scanner.app.orchestrator;

import java.util.Locale;

public enum ScanTier {
    FAST,
    MEDIUM,
    DEEP;

    public static ScanTier fromTargetValue(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FAST", "QUICK" -> FAST;
            case "DEEP", "COMPREHENSIVE" -> DEEP;
            case "MEDIUM", "STANDARD" -> MEDIUM;
            default -> MEDIUM;
        };
    }

    public String toPersistedValue() {
        return name();
    }

    public String toDisplayLabel() {
        return switch (this) {
            case FAST -> "Fast";
            case MEDIUM -> "Medium";
            case DEEP -> "Deep";
        };
    }
}
