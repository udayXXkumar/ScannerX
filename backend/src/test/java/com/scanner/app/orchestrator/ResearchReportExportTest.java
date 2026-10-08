package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchReportExportTest {

    @Test
    void exportsRawToolProvenanceEvidenceAndRunMetadataWithoutDeduplication() {
        FindingRepository findings = mock(FindingRepository.class);
        ScanRepository scans = mock(ScanRepository.class);
        NormalizedReportService service = new NormalizedReportService(findings, scans, new ObjectMapper(), new TierPlanRegistry());

        Scan scan = new Scan();
        scan.setId(42L);
        scan.setName("Juice Deep");
        scan.setTier("DEEP");
        scan.setStatus("COMPLETED");
        scan.setStartedAt(LocalDateTime.parse("2026-10-07T10:00:00"));
        scan.setCompletedAt(LocalDateTime.parse("2026-10-07T10:05:00"));
        Target target = new Target();
        target.setBaseUrl("http://127.0.0.1:3003/");
        scan.setTarget(target);

        Finding first = finding(1L, "Dalfox", "Reflected XSS", "GET parameter 'q'", "{\"method\":\"GET\",\"param\":\"q\",\"payload\":\"x\"}");
        Finding second = finding(2L, "ZAP", "Reflected XSS", "GET parameter 'q'", "{\"method\":\"GET\",\"param\":\"q\",\"payload\":\"x\"}");
        when(findings.findByScanIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(first, second));

        ResearchScanReport report = service.buildResearchReport(scan);

        assertEquals(42L, report.scanId());
        assertEquals("deep", report.scanTier());
        assertEquals(300L, report.scanDurationSeconds());
        assertEquals(2, report.findings().size(), "research export retains raw cross-tool rows");
        assertEquals("Dalfox", report.findings().getFirst().toolName());
        assertEquals("xss", report.findings().getFirst().vulnerabilityClass());
        assertEquals("GET", report.findings().getFirst().method());
        assertEquals("q", report.findings().getFirst().parameter());
        assertEquals(first.getEvidenceData(), report.findings().getFirst().evidence());
        assertNull(report.findings().getFirst().aiSeverity(), "AI assessment stays separate from scanner evidence");
    }

    @Test
    void sqlmapTextEvidencePreservesDetectedMethodAndParameter() {
        FindingRepository findings = mock(FindingRepository.class);
        NormalizedReportService service = new NormalizedReportService(findings, mock(ScanRepository.class), new ObjectMapper(), new TierPlanRegistry());
        Scan scan = new Scan();
        scan.setId(9L);
        scan.setTier("DEEP");
        Finding finding = finding(9L, "sqlmap", "SQL Injection", "GET parameter 'search' appears injectable", "GET parameter 'search' is vulnerable.");
        when(findings.findByScanIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(finding));

        ResearchScanReport.FindingRecord exported = service.buildResearchReport(scan).findings().getFirst();

        assertEquals("sql injection", exported.vulnerabilityClass());
        assertEquals("GET", exported.method());
        assertEquals("search", exported.parameter());
    }

    private Finding finding(Long id, String tool, String title, String description, String evidence) {
        Finding finding = new Finding();
        finding.setId(id);
        finding.setToolName(tool);
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setEvidenceData(evidence);
        finding.setAffectedUrl("http://127.0.0.1:3003/rest/products/search?q=x");
        finding.setSeverity("HIGH");
        finding.setStatus("OPEN");
        return finding;
    }
}
