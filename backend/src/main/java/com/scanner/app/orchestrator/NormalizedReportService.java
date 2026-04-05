package com.scanner.app.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NormalizedReportService {

    private final FindingRepository findingRepository;
    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    public NormalizedReportService(FindingRepository findingRepository, ScanRepository scanRepository, ObjectMapper objectMapper) {
        this.findingRepository = findingRepository;
        this.scanRepository = scanRepository;
        this.objectMapper = objectMapper;
    }

    public NormalizedScanReport buildReport(Scan scan) {
        List<Finding> findings = findingRepository.findByScanIdOrderByCreatedAtDesc(scan.getId());
        Map<String, NormalizedScanReport.FindingEntry> deduped = new LinkedHashMap<>();

        for (Finding finding : findings) {
            if (isExecutionNotice(finding)) {
                continue;
            }

            NormalizedScanReport.FindingEntry entry = new NormalizedScanReport.FindingEntry();
            entry.setType(valueOrFallback(finding.getTitle(), "Security Result"));
            entry.setSeverity(normalizeSeverity(finding.getSeverity()));
            entry.setEndpoint(valueOrFallback(finding.getAffectedUrl(), scan.getTarget() != null ? scan.getTarget().getBaseUrl() : ""));
            entry.setDescription(valueOrFallback(resolveFindingDescription(finding), "No description available."));
            entry.setExploitNarrative(valueOrFallback(finding.getExploitNarrative(), ""));
            entry.setEvidence(valueOrFallback(finding.getEvidenceData(), finding.getDescription()));
            entry.setSource(mapSource(finding));
            deduped.putIfAbsent(buildFingerprint(entry), entry);
        }

        NormalizedScanReport report = new NormalizedScanReport();
        report.setTarget(scan.getTarget() != null ? scan.getTarget().getBaseUrl() : "");
        report.setTier(ScanTier.fromTargetValue(scan.getTier()).name().toLowerCase(Locale.ROOT));
        report.setStatus(normalizeStatus(scan.getStatus()));
        report.setFindings(List.copyOf(deduped.values()));
        populateSummary(report);
        return report;
    }

    public NormalizedScanReport persistReport(Long scanId) {
        Scan scan = scanRepository.findWithContextById(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanId));
        NormalizedScanReport report = buildReport(scan);
        try {
            scan.setNormalizedReportJson(objectMapper.writeValueAsString(report));
            scanRepository.save(scan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize normalized report.", exception);
        }
        return report;
    }

    public NormalizedScanReport readReport(Scan scan) {
        if (scan.getNormalizedReportJson() == null || scan.getNormalizedReportJson().isBlank()) {
            return buildReport(scan);
        }

        try {
            return objectMapper.readValue(scan.getNormalizedReportJson(), NormalizedScanReport.class);
        } catch (Exception ignored) {
            return buildReport(scan);
        }
    }

    private void populateSummary(NormalizedScanReport report) {
        NormalizedScanReport.Summary summary = new NormalizedScanReport.Summary();
        for (NormalizedScanReport.FindingEntry finding : report.getFindings()) {
            switch (normalizeSeverity(finding.getSeverity())) {
                case "critical" -> summary.setCritical(summary.getCritical() + 1);
                case "high" -> summary.setHigh(summary.getHigh() + 1);
                case "medium" -> summary.setMedium(summary.getMedium() + 1);
                case "low" -> summary.setLow(summary.getLow() + 1);
                default -> summary.setInfo(summary.getInfo() + 1);
            }
        }
        report.setSummary(summary);
    }

    private String buildFingerprint(NormalizedScanReport.FindingEntry entry) {
        return String.join("|",
                valueOrFallback(entry.getType(), "type").toLowerCase(Locale.ROOT),
                valueOrFallback(entry.getEndpoint(), "endpoint").toLowerCase(Locale.ROOT),
                valueOrFallback(entry.getDescription(), "description").toLowerCase(Locale.ROOT),
                valueOrFallback(entry.getExploitNarrative(), "exploit").toLowerCase(Locale.ROOT),
                valueOrFallback(entry.getEvidence(), "evidence").toLowerCase(Locale.ROOT)
        );
    }

    private String mapSource(Finding finding) {
        String toolName = String.valueOf(finding.getToolName()).toLowerCase(Locale.ROOT);
        if ("engine".equals(toolName) || "execution".equalsIgnoreCase(finding.getCategory())) {
            return "execution";
        }
        if ("httpx".equals(toolName) || "whatweb".equalsIgnoreCase(toolName)) {
            return "baseline";
        }
        if ("ffuf".equals(toolName)) {
            return "discovery";
        }
        if ("dalfox".equals(toolName) || "sqlmap".equals(toolName)) {
            return "validation";
        }
        if ("zap".equalsIgnoreCase(toolName) && "Active".equalsIgnoreCase(finding.getCategory())) {
            return "active";
        }
        if ("zap".equalsIgnoreCase(toolName) || "nuclei".equalsIgnoreCase(toolName) || "nikto".equalsIgnoreCase(toolName)) {
            return "passive";
        }
        return "execution";
    }

    private String normalizeSeverity(String severity) {
        String normalized = String.valueOf(severity).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "critical" -> "critical";
            case "high" -> "high";
            case "medium", "moderate" -> "medium";
            case "low" -> "low";
            default -> "info";
        };
    }

    private String normalizeStatus(String status) {
        String normalized = String.valueOf(status).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "queued", "pending" -> "queued";
            case "running", "in_progress", "pausing", "paused" -> "running";
            case "completed" -> "completed";
            case "cancelled" -> "cancelled";
            default -> "failed";
        };
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String resolveFindingDescription(Finding finding) {
        if (finding.getAiDescription() != null && !finding.getAiDescription().isBlank()) {
            return finding.getAiDescription();
        }

        return finding.getDescription();
    }

    private boolean isExecutionNotice(Finding finding) {
        return "engine".equalsIgnoreCase(String.valueOf(finding.getToolName()))
                || "execution".equalsIgnoreCase(String.valueOf(finding.getCategory()));
    }
}
