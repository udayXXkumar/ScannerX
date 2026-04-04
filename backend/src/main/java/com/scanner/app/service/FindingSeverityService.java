package com.scanner.app.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class FindingSeverityService {

    public String resolveSeverity(String providedSeverity, String title, String description, String category, String affectedUrl) {
        String normalizedProvided = normalizeSeverity(providedSeverity);
        if (normalizedProvided != null) {
            return normalizedProvided;
        }

        String combined = String.join(" ",
                valueOrEmpty(title),
                valueOrEmpty(description),
                valueOrEmpty(category),
                valueOrEmpty(affectedUrl)
        ).toLowerCase(Locale.ROOT);

        if (containsAny(combined,
                "remote code execution", "rce", "sql injection", "sqli", "command injection",
                "auth bypass", "authentication bypass", "admin takeover", "credential compromise",
                "session hijack", "arbitrary file upload", "deserialization", "prototype pollution")) {
            return "CRITICAL";
        }

        if (containsAny(combined,
                "secret", "token", "apikey", "api key", "password", ".htpasswd", ".bash_history",
                ".mysql_history", ".psql_history", ".sqlite_history", "private key", "credentials",
                "admin", "debug", "swagger", "openapi", "exposed config", "sensitive file",
                "backup", "backdoor", "userdata.json", "users.json", "accounts.json")) {
            return "HIGH";
        }

        if (containsAny(combined,
                "missing", "header", "robots.txt", "security.txt", "interesting", "disclosure",
                "permissions-policy", "content-security-policy", "strict-transport-security",
                "referrer-policy", "x-content-type-options", "directory listing", "public/",
                "informational", "risk: medium", "confidence: medium",
                "login.json", "master.json", "connections.json")) {
            return "MEDIUM";
        }

        if (containsAny(combined,
                "discovered path", "fingerprinted", "technology", "banner", "server", "header",
                "uncommon", "path", "resource", "static asset", "risk: low", "confidence: low")) {
            return "LOW";
        }

        return "INFO";
    }

    public String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }

        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL", "SEVERE" -> "CRITICAL";
            case "HIGH" -> "HIGH";
            case "MEDIUM", "MODERATE" -> "MEDIUM";
            case "LOW" -> "LOW";
            case "INFO", "INFORMATIONAL", "INFORMATION" -> "INFO";
            default -> null;
        };
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
