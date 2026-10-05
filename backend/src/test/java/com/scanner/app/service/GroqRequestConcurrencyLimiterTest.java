package com.scanner.app.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqRequestConcurrencyLimiterTest {

    @Test
    void shouldRespectConfiguredConcurrencyLimit() throws Exception {
        GroqProperties properties = new GroqProperties();
        properties.setMaxConcurrentRequests(2);

        GroqRequestConcurrencyLimiter limiter = new GroqRequestConcurrencyLimiter(properties);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        try {
            for (int i = 0; i < 5; i++) {
                Future<?> future = executor.submit(() -> {
                    try (GroqRequestConcurrencyLimiter.AcquiredRequest ignored = limiter.acquire()) {
                        int current = active.incrementAndGet();
                        maxActive.accumulateAndGet(current, Math::max);
                        Thread.sleep(150L);
                        active.decrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                });
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(maxActive.get() <= 2, "Concurrency limiter should never exceed the configured limit");
    }
}
