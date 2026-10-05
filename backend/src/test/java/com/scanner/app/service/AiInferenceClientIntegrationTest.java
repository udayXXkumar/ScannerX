package com.scanner.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "GROQ_API_KEYS=test1,test2,test3"
})
class AiInferenceClientIntegrationTest {

    @Autowired
    private GroqApiKeyPool pool;

    @Test
    void testPoolSize() {
        assertEquals(3, pool.size(), "Pool should be initialized with 3 keys from GROQ_API_KEYS");
    }
}
