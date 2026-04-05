package com.scanner.app.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FindingTest {

    @Test
    void aiStatusIsNormalizedToCompletedWhenAiContentExists() {
        Finding finding = new Finding();
        finding.setAiEnrichmentStatus("FAILED");
        finding.setAiEnrichmentError("provider timeout");
        finding.setAiDescription("AI-generated summary");

        assertEquals("COMPLETED", finding.getAiEnrichmentStatus());
        assertNull(finding.getAiEnrichmentError());
    }

    @Test
    void rawAiStatusIsReturnedWhenNoAiContentExists() {
        Finding finding = new Finding();
        finding.setAiEnrichmentStatus("FAILED");
        finding.setAiEnrichmentError("provider timeout");

        assertEquals("FAILED", finding.getAiEnrichmentStatus());
        assertEquals("provider timeout", finding.getAiEnrichmentError());
    }
}
