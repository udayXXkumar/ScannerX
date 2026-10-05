package com.scanner.app.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.ai.groq")
public class GroqProperties {
    private List<String> apiKeys = new ArrayList<>();
    private int maxRetries = 3;
    private long retryBackoffMs = 500L;
    private long maxBackoffMs = 30_000L;
    private long requestTimeoutMs = 15_000L;
    private int maxConcurrentRequests = 8;

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys == null ? new ArrayList<>() : apiKeys.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            this.apiKeys = new ArrayList<>();
            return;
        }
        this.apiKeys = List.of(apiKey.trim());
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = Math.max(200L, retryBackoffMs);
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = Math.max(500L, maxBackoffMs);
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(1000L, requestTimeoutMs);
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
    }
}
