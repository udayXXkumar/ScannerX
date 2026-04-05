package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NucleiExecutorTest {

    private final NucleiExecutor executor = new NucleiExecutor(null, null, new ObjectMapper());

    @Test
    void resolveBatchTimeoutReturnsNullWhenStepHasNoOverallDeadline() {
        PlanStep step = new PlanStep(
                "nuclei-discovered",
                "Endpoint Checks",
                10,
                null,
                false,
                Map.of("batchTimeoutSeconds", "18")
        );

        Duration timeout = executor.resolveBatchTimeout(step, Long.MAX_VALUE);

        assertNull(timeout);
    }

    @Test
    void resolveBatchTimeoutStillCapsToConfiguredBatchWindowWhenOverallDeadlineExists() {
        PlanStep step = new PlanStep(
                "nuclei-discovered",
                "Endpoint Checks",
                10,
                Duration.ofSeconds(60),
                false,
                Map.of("batchTimeoutSeconds", "18")
        );

        Duration timeout = executor.resolveBatchTimeout(step, System.nanoTime() + Duration.ofSeconds(60).toNanos());

        assertEquals(Duration.ofSeconds(18), timeout);
    }
}
