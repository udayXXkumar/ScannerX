package com.scanner.app.service;

import java.util.concurrent.atomic.AtomicInteger;

public class GroqApiKeyState {
    public enum Status {
        HEALTHY, COOLDOWN, INVALID
    }

    private final String key;
    private final String maskedKey;
    private volatile Status status;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger rateLimitHits = new AtomicInteger(0);

    private volatile long lastUsedAt = 0L;
    private volatile long lastSuccessAt = 0L;
    private volatile long lastFailureAt = 0L;
    private volatile long cooldownUntil = 0L;
    private volatile long rateLimitResetAt = 0L;

    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private volatile int estimatedAvailability = 100;
    private volatile int healthScore = 100;

    public GroqApiKeyState(String key, String maskedKey) {
        this.key = key;
        this.maskedKey = maskedKey;
        this.status = Status.HEALTHY;
        this.estimatedAvailability = 100;
    }

    public String getKey() {
        return key;
    }

    public String getMaskedKey() {
        return maskedKey;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.HEALTHY) {
            if (cooldownUntil <= System.currentTimeMillis()) {
                cooldownUntil = 0L;
            }
        }
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    public int getSuccessfulRequests() {
        return successfulRequests.get();
    }

    public int getFailedRequests() {
        return failedRequests.get();
    }

    public int getRateLimitHits() {
        return rateLimitHits.get();
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public long getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(long lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public long getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(long lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
        if (cooldownUntil > System.currentTimeMillis()) {
            this.status = Status.COOLDOWN;
        } else {
            this.status = Status.HEALTHY;
        }
    }

    public long getRateLimitResetAt() {
        return rateLimitResetAt;
    }

    public void setRateLimitResetAt(long rateLimitResetAt) {
        this.rateLimitResetAt = rateLimitResetAt;
    }

    public int getInFlightRequests() {
        return inFlightRequests.get();
    }

    public void incrementInFlight() {
        inFlightRequests.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlightRequests.updateAndGet(value -> Math.max(0, value - 1));
    }

    public int getEstimatedAvailability() {
        return estimatedAvailability;
    }

    public void setEstimatedAvailability(int estimatedAvailability) {
        this.estimatedAvailability = Math.max(0, Math.min(100, estimatedAvailability));
    }

    public int getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(int healthScore) {
        this.healthScore = Math.max(0, Math.min(100, healthScore));
    }

    public boolean isEligible(long nowMs) {
        if (this.status == Status.INVALID) {
            return false;
        }
        if (this.status == Status.COOLDOWN && this.cooldownUntil > nowMs) {
            return false;
        }
        if (this.status == Status.COOLDOWN && this.cooldownUntil <= nowMs) {
            this.status = Status.HEALTHY;
        }
        return true;
    }

    public void recordSuccess() {
        successfulRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        consecutiveFailures.set(0);
        lastSuccessAt = System.currentTimeMillis();
        status = Status.HEALTHY;
        cooldownUntil = 0L;
        estimatedAvailability = Math.min(100, estimatedAvailability + 5);
        healthScore = Math.min(100, healthScore + 8);
    }

    public void recordFailure(boolean isRateLimit, boolean isAuthError) {
        failedRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        lastFailureAt = System.currentTimeMillis();

        if (isAuthError) {
            status = Status.INVALID;
            healthScore = 0;
            cooldownUntil = 0L;
            return;
        }

        if (isRateLimit) {
            rateLimitHits.incrementAndGet();
            status = Status.COOLDOWN;
            healthScore = Math.max(0, healthScore - 12);
            return;
        }

        status = Status.HEALTHY;
        healthScore = Math.max(0, healthScore - 18);
        estimatedAvailability = Math.max(0, estimatedAvailability - 15);
    }

    public void incrementTotalRequests() {
        totalRequests.incrementAndGet();
    }
}
