package com.scanner.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GroqApiKeyPool {
    private static final Logger logger = LoggerFactory.getLogger(GroqApiKeyPool.class);
    private static final double IN_FLIGHT_PENALTY = 25.0;
    private static final double FAILURE_PENALTY = 15.0;
    private static final double RATE_LIMIT_PENALTY = 30.0;
    private static final double IDLE_BONUS = 0.75;

    private final List<GroqApiKeyState> keys = new ArrayList<>();

    public void initialize(List<String> rawKeys) {
        synchronized (keys) {
            keys.clear();
            Set<String> seen = new LinkedHashSet<>();
            int i = 1;
            for (String key : rawKeys) {
                if (key == null) {
                    continue;
                }
                String normalized = key.trim();
                if (normalized.isEmpty() || !seen.add(normalized)) {
                    continue;
                }
                String masked = "key-" + String.format("%02d", i++);
                keys.add(new GroqApiKeyState(normalized, masked));
            }
        }
    }

    public int size() {
        synchronized (keys) {
            return keys.size();
        }
    }

    public List<GroqApiKeyState> getAllKeys() {
        synchronized (keys) {
            return new ArrayList<>(keys);
        }
    }

    public List<GroqApiKeyState> getEligibleKeys() {
        long now = System.currentTimeMillis();
        synchronized (keys) {
            List<GroqApiKeyState> eligible = new ArrayList<>();
            for (GroqApiKeyState keyState : keys) {
                if (keyState.isEligible(now)) {
                    eligible.add(keyState);
                }
            }
            return eligible;
        }
    }

    public GroqApiKeyState acquireBestKey() {
        long now = System.currentTimeMillis();
        GroqApiKeyState bestKey = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        synchronized (keys) {
            for (GroqApiKeyState state : keys) {
                if (!state.isEligible(now)) {
                    continue;
                }

                double score = calculateScore(state, now);
                if (score > bestScore) {
                    bestScore = score;
                    bestKey = state;
                }
            }

            if (bestKey == null) {
                return null;
            }

            bestKey.incrementInFlight();
            bestKey.setLastUsedAt(now);
            logger.debug("[GroqPool] selected {} with score {} (inFlight={}, failures={}, health={})",
                    bestKey.getMaskedKey(), String.format("%.2f", bestScore),
                    bestKey.getInFlightRequests(), bestKey.getConsecutiveFailures(), bestKey.getHealthScore());
            return bestKey;
        }
    }

    public long getEarliestAvailableTime() {
        long now = System.currentTimeMillis();
        long earliest = Long.MAX_VALUE;
        synchronized (keys) {
            for (GroqApiKeyState state : keys) {
                if (state.getStatus() == GroqApiKeyState.Status.INVALID) {
                    continue;
                }
                if (state.getStatus() == GroqApiKeyState.Status.COOLDOWN && state.getCooldownUntil() > now) {
                    earliest = Math.min(earliest, state.getCooldownUntil());
                }
            }
        }
        return earliest == Long.MAX_VALUE ? now : earliest;
    }

    public boolean hasAvailableKey() {
        return !getEligibleKeys().isEmpty();
    }

    public long getNextAvailableDelayMs() {
        long now = System.currentTimeMillis();
        long earliest = getEarliestAvailableTime();
        if (earliest <= now) {
            return 0L;
        }
        return earliest - now;
    }

    private double calculateScore(GroqApiKeyState state, long now) {
        long idleMs = Math.max(0L, now - state.getLastUsedAt());
        double idleBonus = Math.min(60.0, idleMs / 1000.0) * IDLE_BONUS;
        double healthScore = state.getHealthScore();
        double availability = state.getEstimatedAvailability();
        double inflightPenalty = state.getInFlightRequests() * IN_FLIGHT_PENALTY;
        double failurePenalty = state.getConsecutiveFailures() * FAILURE_PENALTY;
        double rateLimitPenalty = state.getRateLimitHits() * RATE_LIMIT_PENALTY;

        return healthScore + idleBonus + (availability / 2.0) - inflightPenalty - failurePenalty - rateLimitPenalty;
    }
}
