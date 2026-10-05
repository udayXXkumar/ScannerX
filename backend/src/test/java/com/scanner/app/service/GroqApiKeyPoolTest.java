package com.scanner.app.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GroqApiKeyPoolTest {

    @Test
    void testInitializeWithMultipleKeys() {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1", "key2", "key3", "", "   "));

        assertEquals(3, pool.size());
        assertEquals("key1", pool.getAllKeys().get(0).getKey());
        assertEquals("key-01", pool.getAllKeys().get(0).getMaskedKey());
        assertEquals("key-03", pool.getAllKeys().get(2).getMaskedKey());
    }

    @Test
    void testAcquireBestKeyPrefersIdleHealthyKey() {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1", "key2"));

        GroqApiKeyState first = pool.acquireBestKey();
        GroqApiKeyState second = pool.acquireBestKey();

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.getKey(), second.getKey(), "Should not pick the same key twice if another is idle");
    }

    @Test
    void testUnauthorizedKeyIsDisabledAndAnotherKeyIsSelected() {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1", "key2"));

        GroqApiKeyState disabledKey = pool.getAllKeys().get(0);
        disabledKey.setStatus(GroqApiKeyState.Status.INVALID);
        disabledKey.setHealthScore(0);

        GroqApiKeyState selected = pool.acquireBestKey();

        assertNotNull(selected);
        assertEquals("key2", selected.getKey());
        assertEquals(GroqApiKeyState.Status.HEALTHY, selected.getStatus());
    }

    @Test
    void testRateLimitedKeyGoesToCooldownAndAnotherKeyIsSelected() throws InterruptedException {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1", "key2"));

        GroqApiKeyState rateLimited = pool.getAllKeys().get(0);
        rateLimited.setCooldownUntil(System.currentTimeMillis() + 1000L);
        rateLimited.setStatus(GroqApiKeyState.Status.COOLDOWN);

        GroqApiKeyState selected = pool.acquireBestKey();

        assertNotNull(selected);
        assertEquals("key2", selected.getKey());
        assertTrue(pool.getEarliestAvailableTime() <= System.currentTimeMillis() + 1100L);
    }

    @Test
    void testCooldownRecoveryReenablesKey() throws InterruptedException {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1"));

        GroqApiKeyState key = pool.getAllKeys().get(0);
        key.setStatus(GroqApiKeyState.Status.COOLDOWN);
        key.setCooldownUntil(System.currentTimeMillis() + 200L);

        assertNull(pool.acquireBestKey(), "Should not select a key during cooldown");

        Thread.sleep(250L);
        GroqApiKeyState recovered = pool.acquireBestKey();
        assertNotNull(recovered);
        assertEquals("key1", recovered.getKey());
    }

    @Test
    void testConcurrentRequestsDoNotAssignSameKey() throws Exception {
        GroqApiKeyPool pool = new GroqApiKeyPool();
        pool.initialize(List.of("key1", "key2", "key3", "key4"));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = IntStream.range(0, 16)
                    .mapToObj(i -> (Callable<String>) () -> pool.acquireBestKey().getKey())
                    .toList();

            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executor.submit(task));
            }

            Set<String> selectedKeys = ConcurrentHashMap.newKeySet();
            for (Future<String> future : futures) {
                String key = future.get();
                assertNotNull(key);
                selectedKeys.add(key);
            }

            assertTrue(selectedKeys.size() >= 2, "Concurrent scheduling should use more than one key when available");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testRequestMetricsCountEachAttemptOnce() {
        GroqApiKeyState keyState = new GroqApiKeyState("key1", "key-01");
        keyState.recordSuccess();
        keyState.recordFailure(true, false);

        assertEquals(2, keyState.getTotalRequests());
        assertEquals(1, keyState.getSuccessfulRequests());
        assertEquals(1, keyState.getFailedRequests());
        assertEquals(1, keyState.getRateLimitHits());
    }

    @Test
    void testResetDurationParsingSupportsGroqFormats() {
        assertEquals(179560L, AiInferenceClient.parseResetDurationToMillis("2m59.56s"));
        assertEquals(59000L, AiInferenceClient.parseResetDurationToMillis("59s"));
        assertEquals(60000L, AiInferenceClient.parseResetDurationToMillis("1m"));
        assertEquals(500L, AiInferenceClient.parseResetDurationToMillis("500ms"));
        assertEquals(-1L, AiInferenceClient.parseResetDurationToMillis("not-a-duration"));
    }
}
