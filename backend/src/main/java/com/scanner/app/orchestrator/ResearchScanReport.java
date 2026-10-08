package com.scanner.app.orchestrator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Lossless, per-scan export used for reproducible benchmark evaluation. */
public record ResearchScanReport(
        String schemaVersion,
        Long scanId,
        String scanName,
        String scanTier,
        String scanStatus,
        String targetUrl,
        LocalDateTime scanStartedAt,
        LocalDateTime scanCompletedAt,
        Long scanDurationSeconds,
        boolean aiEnrichmentCancelled,
        Map<String, Object> scanConfiguration,
        Map<String, String> toolVersions,
        List<FindingRecord> findings
) {
    public record FindingRecord(
            Long findingId,
            String toolName,
            String sourceCategory,
            String title,
            String vulnerabilityClass,
            String claimType,
            String severity,
            String status,
            String endpoint,
            String method,
            String parameter,
            String evidence,
            String description,
            String cweId,
            String owaspCategory,
            String aiSeverity,
            String aiSeverityReason,
            Integer aiPriorityScore,
            String aiPriorityReason,
            Long duplicateOfFindingId,
            String aiEnrichmentStatus,
            LocalDateTime createdAt
    ) {
    }
}
