package com.scanner.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.orchestrator.NormalizedScanReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigObjectMapperTest {

    @Test
    void configuredMapperSerializesAndReadsNormalizedReportTimestamps() throws Exception {
        ObjectMapper mapper = new SecurityConfig(mock(JwtFilter.class), "", "").objectMapper();
        LocalDateTime started = LocalDateTime.parse("2026-10-07T17:45:46");
        NormalizedScanReport report = new NormalizedScanReport();
        report.setScanStartedAt(started);

        String json = mapper.writeValueAsString(report);
        NormalizedScanReport restored = mapper.readValue(json, NormalizedScanReport.class);

        assertTrue(json.contains("2026-10-07T17:45:46"));
        assertEquals(started, restored.getScanStartedAt());
    }
}
