package com.scanner.app.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.time.Duration;

@Service
public class NormalizedReportService {

    private final FindingRepository findingRepository;
    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;
    private final TierPlanRegistry tierPlanRegistry;

    public NormalizedReportService(FindingRepository findingRepository, ScanRepository scanRepository, ObjectMapper objectMapper,
                                   TierPlanRegistry tierPlanRegistry) {
        this.findingRepository = findingRepository;
        this.scanRepository = scanRepository;
        this.objectMapper = objectMapper;
        this.tierPlanRegistry = tierPlanRegistry;
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
            entry.setToolName(finding.getToolName());
            entry.setSeverity(normalizeSeverity(finding.getSeverity()));
            entry.setAiSeverity(finding.getAiSeverity());
            entry.setAiEnrichmentStatus(finding.getAiEnrichmentStatus());
            entry.setAiPriorityScore(finding.getAiPriorityScore());
            entry.setAiPriorityReason(finding.getAiPriorityReason());
            entry.setDuplicate(finding.getAiDuplicateOfId() != null);
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
        report.setAiEnrichmentCancelled(Boolean.TRUE.equals(scan.getAiEnrichmentCancelled()));
        report.setScanId(scan.getId());
        report.setScanName(scan.getName());
        report.setScanStartedAt(scan.getStartedAt());
        report.setScanCompletedAt(scan.getCompletedAt());
        report.setScanDurationSeconds(durationSeconds(scan));
        report.setFindings(List.copyOf(deduped.values()));
        populateSummary(report);
        return report;
    }

    /** Returns all raw finding rows for research; unlike the dashboard report this never deduplicates. */
    public ResearchScanReport buildResearchReport(Scan scan) {
        List<Finding> findings = findingRepository.findByScanIdOrderByCreatedAtDesc(scan.getId());
        List<ResearchScanReport.FindingRecord> records = findings.stream()
                .filter(finding -> !isExecutionNotice(finding))
                .map(this::toResearchFinding)
                .toList();
        ScanTier tier = ScanTier.fromTargetValue(scan.getTier() == null ? scan.getProfileType() : scan.getTier());
        boolean timeoutsEnabled = scan.getTimeoutsEnabled() == null || scan.getTimeoutsEnabled();
        return new ResearchScanReport(
                "1.0",
                scan.getId(),
                scan.getName(),
                tier.name().toLowerCase(Locale.ROOT),
                scan.getStatus(),
                scan.getTarget() == null ? null : scan.getTarget().getBaseUrl(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                durationSeconds(scan),
                Boolean.TRUE.equals(scan.getAiEnrichmentCancelled()),
                planSnapshot(tierPlanRegistry.planFor(tier, timeoutsEnabled), timeoutsEnabled),
                toolVersions(),
                records
        );
    }

    /** Tool versions come from the exact binaries/images executed for this scan, not the host PATH. */
    private Map<String, String> toolVersions() {
        // ToolExecutionService records execution output, while image/binary version capture is
        // handled by the research toolchain manifest. Leave unavailable versions explicit.
        Map<String, String> versions = new LinkedHashMap<>();
        for (String name : List.of("httpx", "whatweb", "ffuf", "nuclei", "nikto", "zap", "dalfox", "sqlmap")) {
            versions.put(name, "unavailable: capture with research/juice-shop-v20.2.0/tools/capture_toolchain.py");
        }
        return versions;
    }

    private Map<String, Object> planSnapshot(TierPlan plan, boolean timeoutsEnabled) {
        List<Map<String, Object>> stages = new ArrayList<>();
        for (PlanStage stage : plan.stages()) {
            List<Map<String, Object>> steps = stage.steps().stream().map(step -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", step.key());
                item.put("label", step.label());
                item.put("retryable", step.retryable());
                item.put("timeoutSeconds", step.timeout() == null ? null : step.timeout().toSeconds());
                item.put("settings", step.settings());
                return item;
            }).toList();
            stages.add(Map.of("order", stage.order(), "label", stage.label(), "steps", steps));
        }
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("tier", plan.tier().name().toLowerCase(Locale.ROOT));
        configuration.put("timeoutsEnabled", timeoutsEnabled);
        configuration.put("hardTimeoutSeconds", plan.hardTimeout() == null ? null : plan.hardTimeout().toSeconds());
        configuration.put("stages", stages);
        return configuration;
    }

    private ResearchScanReport.FindingRecord toResearchFinding(Finding finding) {
        String evidence = finding.getEvidenceData();
        String method = null;
        String parameter = null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = evidence == null ? null : objectMapper.readTree(evidence);
            if (root != null && root.isObject()) {
                method = text(root, "method");
                parameter = text(root, "param");
                if (root.path("sampleEvidence").isArray() && !root.path("sampleEvidence").isEmpty()) {
                    String sample = root.path("sampleEvidence").get(0).asText("");
                    if (sample.startsWith("{")) {
                        com.fasterxml.jackson.databind.JsonNode raw = objectMapper.readTree(sample);
                        method = firstNonBlank(method, text(raw, "method"));
                        parameter = firstNonBlank(parameter, text(raw, "param"));
                    }
                }
            }
        } catch (Exception ignored) {
            // The original evidence is still exported unchanged when it is not JSON.
        }
        if (method == null && evidence != null) {
            Matcher match = Pattern.compile("(?i)\\b(GET|POST|PUT|PATCH|DELETE)\\s+parameter\\s+'([^']+)'").matcher(evidence);
            if (match.find()) {
                method = match.group(1).toUpperCase(Locale.ROOT);
                parameter = firstNonBlank(parameter, match.group(2));
            }
        }
        if (method == null && "sqlmap".equalsIgnoreCase(finding.getToolName())) {
            method = "GET";
        }
        if (parameter == null && finding.getAffectedUrl() != null) {
            try {
                String query = URI.create(finding.getAffectedUrl()).getRawQuery();
                if (query != null && !query.isBlank()) {
                    parameter = query.split("[=&]", 2)[0];
                }
            } catch (Exception ignored) {
                // Keep the request parameter unavailable rather than guessing.
            }
        }
        String vulnerabilityClass = inferVulnerabilityClass(finding);
        String claimType = vulnerabilityClass == null ? "observation" : "vulnerability";
        return new ResearchScanReport.FindingRecord(
                finding.getId(), finding.getToolName(), finding.getCategory(), finding.getTitle(),
                vulnerabilityClass, claimType, finding.getSeverity(), finding.getStatus(),
                finding.getAffectedUrl(), method, parameter, evidence, finding.getDescription(),
                finding.getCweId(), finding.getOwaspCategory(), finding.getAiSeverity(),
                finding.getAiSeverityReason(), finding.getAiPriorityScore(), finding.getAiPriorityReason(),
                finding.getAiDuplicateOfId(), finding.getAiEnrichmentStatus(), finding.getCreatedAt()
        );
    }

    private String inferVulnerabilityClass(Finding finding) {
        String cwe = String.valueOf(finding.getCweId()).toLowerCase(Locale.ROOT).replace("cwe-", "").trim();
        String byCwe = switch (cwe) {
            case "79" -> "xss";
            case "89" -> "sql injection";
            case "352" -> "csrf";
            case "22", "23", "36" -> "path traversal";
            case "918" -> "ssrf";
            case "611" -> "xxe";
            case "284", "285", "862", "863" -> "authorization";
            case "287", "306", "384" -> "authentication/session";
            case "200", "209", "532", "497", "538" -> "information disclosure";
            case "16", "693", "1021", "942" -> "security misconfiguration";
            case "798", "259" -> "hard-coded/weak credentials";
            default -> null;
        };
        if (byCwe != null) return byCwe;
        String title = norm(finding.getTitle());
        String text = (title + " " + norm(finding.getDescription())).toLowerCase(Locale.ROOT);
        if (text.contains("sql injection") || text.contains("sqli")) return "sql injection";
        if (text.contains("cross-site scripting") || text.contains("xss")) return "xss";
        if (text.contains("ssrf")) return "ssrf";
        if (text.contains("xxe")) return "xxe";
        if (text.contains("path traversal") || text.contains("local file read")) return "path traversal";
        if (text.contains("cors") || text.contains("security header") || text.contains("security misconfiguration")) return "security misconfiguration";
        if (text.contains("authorization") || text.contains("access control") || text.contains("idor")) return "authorization";
        if (text.contains("information disclosure") || text.contains("sensitive data") || text.contains("exposed file")) return "information disclosure";
        if (text.contains("injection")) return "injection (unclassified)";
        if (text.contains("csrf")) return "csrf";
        return null;
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String key) {
        String value = node.path(key).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String norm(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private Long durationSeconds(Scan scan) {
        if (scan.getStartedAt() == null || scan.getCompletedAt() == null) return null;
        return Duration.between(scan.getStartedAt(), scan.getCompletedAt()).toSeconds();
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
                valueOrFallback(entry.getToolName(), "tool").toLowerCase(Locale.ROOT),
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
