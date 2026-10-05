package com.scanner.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "GROQ_API_KEYS=key1,key2,key3",
        "GROQ_MAX_CONCURRENT_REQUESTS=5",
        "GROQ_REQUEST_TIMEOUT_MS=7500"
})
class GroqPropertiesBindingTest {

    @Autowired
    private GroqProperties groqProperties;

    @Autowired
    private GroqApiKeyPool groqApiKeyPool;

    @Test
    void shouldBindGroqApiKeysFromEnvironmentProperty() {
        assertEquals(List.of("key1", "key2", "key3"), groqProperties.getApiKeys());
        assertEquals(3, groqApiKeyPool.size());
        assertEquals(5, groqProperties.getMaxConcurrentRequests());
        assertEquals(7500L, groqProperties.getRequestTimeoutMs());
    }
}
