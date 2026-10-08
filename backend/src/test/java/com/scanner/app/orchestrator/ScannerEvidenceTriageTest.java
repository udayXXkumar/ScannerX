package com.scanner.app.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerEvidenceTriageTest {

    @Test
    void specializedActiveChecksSkipSocketIoPollingTransport() {
        String transport = "http://127.0.0.1:3000/socket.io/?EIO=4&transport=polling&sid=abc";
        String applicationEndpoint = "http://127.0.0.1:3000/rest/products/search?q=test";

        assertTrue(DalfoxExecutor.isSocketIoTransportUrl(transport));
        assertTrue(SqlmapExecutor.isSocketIoTransportUrl(transport));
        assertFalse(DalfoxExecutor.isSocketIoTransportUrl(applicationEndpoint));
        assertFalse(SqlmapExecutor.isSocketIoTransportUrl(applicationEndpoint));
    }

    @Test
    void zapSocketIoSessionIdIsNotReportedAsSessionLeak() {
        ZapApiClient.ZapAlert transportAlert = new ZapApiClient.ZapAlert(
                "Session ID in URL Rewrite", "Informational", "", "Low", "", "description", "solution",
                "", "sid", "sid=abc", "", "http://127.0.0.1:3000/socket.io/?sid=abc", "{}"
        );
        ZapApiClient.ZapAlert normalAlert = new ZapApiClient.ZapAlert(
                "Session ID in URL Rewrite", "Informational", "", "Low", "", "description", "solution",
                "", "sid", "sid=abc", "", "http://127.0.0.1:3000/account?sid=abc", "{}"
        );

        assertTrue(ZapExecutor.isSocketIoTransportSessionId(transportAlert));
        assertFalse(ZapExecutor.isSocketIoTransportSessionId(normalAlert));
    }

    @Test
    void niktoInterestingPathRemainsLowConfidenceReviewLead() {
        NiktoExecutor executor = new NiktoExecutor(null, null, null);

        assertTrue(executor.shouldCreateFinding("+ /backup/: This might be interesting"));
        org.junit.jupiter.api.Assertions.assertEquals("LOW", executor.severityFor("+ /backup/: This might be interesting"));
        assertFalse(executor.shouldCreateFinding("+ Target Port: 3000"));
    }
}
