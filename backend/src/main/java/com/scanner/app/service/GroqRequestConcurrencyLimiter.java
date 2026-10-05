package com.scanner.app.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class GroqRequestConcurrencyLimiter {
    private final Semaphore semaphore;

    public GroqRequestConcurrencyLimiter(GroqProperties groqProperties) {
        int maxConcurrentRequests = groqProperties == null ? 1 : Math.max(1, groqProperties.getMaxConcurrentRequests());
        this.semaphore = new Semaphore(maxConcurrentRequests, true);
    }

    public AcquiredRequest acquire() throws InterruptedException {
        semaphore.acquire();
        return new AcquiredRequest(this);
    }

    public static final class AcquiredRequest implements AutoCloseable {
        private final GroqRequestConcurrencyLimiter limiter;
        private boolean released;

        private AcquiredRequest(GroqRequestConcurrencyLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        public void close() {
            if (!released) {
                limiter.semaphore.release();
                released = true;
            }
        }
    }
}
