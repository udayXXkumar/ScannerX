package com.scanner.app.service;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.orchestrator.NormalizedReportService;
import com.scanner.app.orchestrator.NormalizedScanReport;
import com.scanner.app.rest.ReportSummaryResponse;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.TargetRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ReportService {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final FindingRepository findingRepository;
    private final ScanRepository scanRepository;
    private final NormalizedReportService normalizedReportService;
    private final TargetRepository targetRepository;

    public ReportService(
            FindingRepository findingRepository,
            ScanRepository scanRepository,
            NormalizedReportService normalizedReportService,
            TargetRepository targetRepository
    ) {
        this.findingRepository = findingRepository;
        this.scanRepository = scanRepository;
        this.normalizedReportService = normalizedReportService;
        this.targetRepository = targetRepository;
    }

    public String generateCsvReportForScan(Long scanId) {
        Scan scan = scanRepository.findWithContextById(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found."));
        NormalizedScanReport report = normalizedReportService.readReport(scan);
        StringBuilder sb = new StringBuilder();

        sb.append("Target,").append(escapeCsv(report.getTarget())).append("\n");
        sb.append("Tier,").append(escapeCsv(report.getTier())).append("\n");
        sb.append("Status,").append(escapeCsv(report.getStatus())).append("\n");
        sb.append("Critical,").append(report.getSummary().getCritical()).append("\n");
        sb.append("High,").append(report.getSummary().getHigh()).append("\n");
        sb.append("Medium,").append(report.getSummary().getMedium()).append("\n");
        sb.append("Low,").append(report.getSummary().getLow()).append("\n");
        sb.append("Info,").append(report.getSummary().getInfo()).append("\n\n");
        sb.append("Type,Severity,Endpoint,Description,Exploit Narrative,Evidence,Source\n");

        for (NormalizedScanReport.FindingEntry finding : report.getFindings()) {
            sb.append(escapeCsv(finding.getType())).append(",");
            sb.append(escapeCsv(finding.getSeverity())).append(",");
            sb.append(escapeCsv(finding.getEndpoint())).append(",");
            sb.append(escapeCsv(finding.getDescription())).append(",");
            sb.append(escapeCsv(finding.getExploitNarrative())).append(",");
            sb.append(escapeCsv(finding.getEvidence())).append(",");
            sb.append(escapeCsv(finding.getSource()));
            sb.append("\n");
        }

        return sb.toString();
    }

    public NormalizedScanReport generateJsonReportForScan(Long scanId) {
        Scan scan = scanRepository.findWithContextById(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found."));
        return normalizedReportService.readReport(scan);
    }

    public ReportSummaryResponse buildSummary(Long userId, Long targetId, Long scanId) {
        List<Scan> completedScans = scanRepository.findWithContextByUserIdAndStatusOrderByCreatedAtDesc(userId, "COMPLETED");
        if (scanId != null) {
            Scan selectedScan = scanRepository.findWithContextByIdAndUserId(scanId, userId)
                    .filter(scan -> "COMPLETED".equalsIgnoreCase(scan.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Completed scan not found."));
            completedScans = completedScans.stream().filter(scan -> scan.getId().equals(selectedScan.getId())).toList();
            targetId = selectedScan.getTarget().getId();
        }

        if (targetId != null) {
            Long effectiveTargetId = targetId;
            completedScans = completedScans.stream()
                    .filter(scan -> scan.getTarget() != null && effectiveTargetId.equals(scan.getTarget().getId()))
                    .toList();
        }

        List<Finding> findings = sanitizeFindings(findingRepository.findVisibleFindings(userId, targetId, scanId, true));
        findings = findings.stream()
                .sorted(Comparator.comparing(Finding::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        ReportSummaryResponse response = new ReportSummaryResponse();
        response.setGeneratedAt(LocalDateTime.now());
        response.setTargetId(targetId);
        response.setScanId(scanId);
        response.setFindings(findings);
        response.setTotalScans(completedScans.size());
        response.setTotalTargets(targetId != null
                ? 1
                : completedScans.stream()
                    .map(scan -> scan.getTarget() == null ? null : scan.getTarget().getId())
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count());
        response.setTotalFindings(findings.size());
        response.setOpenFindings(findings.stream().filter(finding -> !isResolvedStatus(finding.getStatus())).count());
        response.setResolvedFindings(findings.stream().filter(finding -> isResolvedStatus(finding.getStatus())).count());
        response.setCriticalFindings(countBySeverity(findings, "CRITICAL"));
        response.setHighFindings(countBySeverity(findings, "HIGH"));
        response.setMediumFindings(countBySeverity(findings, "MEDIUM"));
        response.setLowFindings(countBySeverity(findings, "LOW"));
        response.setInformationalFindings(
                findings.stream().filter(finding -> {
                    String severity = String.valueOf(finding.getSeverity()).toUpperCase(Locale.ROOT);
                    return "INFO".equals(severity) || "INFORMATIONAL".equals(severity);
                }).count()
        );

        Scan firstScan = completedScans.isEmpty() ? null : completedScans.getFirst();
        String resolvedTargetName = "All targets";
        if (targetId != null) {
            resolvedTargetName = targetRepository.findByIdAndUserId(targetId, userId)
                    .map(target -> target.getName() == null || target.getName().isBlank() ? "Selected target" : target.getName().trim())
                    .orElseGet(() -> firstScan != null && firstScan.getTarget() != null && firstScan.getTarget().getName() != null
                            ? firstScan.getTarget().getName()
                            : "Selected target");
        }
        response.setTargetName(resolvedTargetName);
        response.setScanName(resolvedTargetName);
        response.setScopeLabel(targetId != null
                ? response.getTargetName() + " · All completed runs"
                : "All targets");

        return response;
    }

    public String generateCsvForSummary(ReportSummaryResponse summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Scope,").append(escapeCsv(summary.getScopeLabel())).append("\n");
        sb.append("Target,").append(escapeCsv(summary.getTargetName())).append("\n");
        sb.append("Generated At,").append(summary.getGeneratedAt()).append("\n");
        sb.append("Total Targets,").append(summary.getTotalTargets()).append("\n");
        sb.append("Total Scans,").append(summary.getTotalScans()).append("\n");
        sb.append("Total Findings,").append(summary.getTotalFindings()).append("\n");
        sb.append("Open Findings,").append(summary.getOpenFindings()).append("\n");
        sb.append("Resolved Findings,").append(summary.getResolvedFindings()).append("\n");
        sb.append("Critical,").append(summary.getCriticalFindings()).append("\n");
        sb.append("High,").append(summary.getHighFindings()).append("\n");
        sb.append("Medium,").append(summary.getMediumFindings()).append("\n");
        sb.append("Low,").append(summary.getLowFindings()).append("\n");
        sb.append("Informational,").append(summary.getInformationalFindings()).append("\n\n");
        sb.append("ID,Target Name,Category,Title,Severity,Status,Affected URL,CWE,OWASP,Created At,Description,Exploit Narrative\n");

        for (Finding finding : summary.getFindings()) {
            String targetName = finding.getTarget() != null ? finding.getTarget().getName() : summary.getTargetName();
            sb.append(escapeCsv(String.valueOf(finding.getId()))).append(",");
            sb.append(escapeCsv(targetName)).append(",");
            sb.append(escapeCsv(finding.getCategory())).append(",");
            sb.append(escapeCsv(finding.getTitle())).append(",");
            sb.append(escapeCsv(finding.getSeverity())).append(",");
            sb.append(escapeCsv(finding.getStatus())).append(",");
            sb.append(escapeCsv(finding.getAffectedUrl())).append(",");
            sb.append(escapeCsv(finding.getCweId())).append(",");
            sb.append(escapeCsv(finding.getOwaspCategory())).append(",");
            sb.append(escapeCsv(finding.getCreatedAt() == null ? "" : finding.getCreatedAt().toString())).append(",");
            sb.append(escapeCsv(resolveFindingDescription(finding))).append(",");
            sb.append(escapeCsv(resolveExploitNarrative(finding)));
            sb.append("\n");
        }

        return sb.toString();
    }

    public byte[] generatePdfForSummary(ReportSummaryResponse summary) {
        String html = generateHtmlForSummary(summary);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("Unable to generate PDF report.", exception);
        }
    }

    private String escapeCsv(String data) {
        if (data == null) return "";
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public String generateHtmlSummary(Long scanId, boolean detailed) {
        Scan scan = scanRepository.findWithContextById(scanId).orElse(null);
        if (scan == null) return "<h1>Scan not found</h1>";

        List<Finding> findings = sanitizeFindings(findingRepository.findByScanId(scanId));
        long critical = findings.stream().filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity())).count();
        long high = findings.stream().filter(f -> "HIGH".equalsIgnoreCase(f.getSeverity())).count();
        long medium = findings.stream().filter(f -> "MEDIUM".equalsIgnoreCase(f.getSeverity())).count();
        long low = findings.stream().filter(f -> "LOW".equalsIgnoreCase(f.getSeverity())).count();

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body { font-family: Arial, sans-serif; color: #333; margin: 40px; }")
            .append("h1, h2, h3 { color: #2c3e50; }")
            .append(".stats { display: flex; gap: 20px; margin-bottom: 30px; }")
            .append(".stat-box { padding: 15px; border-radius: 8px; text-align: center; font-weight: bold; width: 100px; }")
            .append(".critical { background: #fee2e2; color: #991b1b; }")
            .append(".high { background: #ffedd5; color: #9a3412; }")
            .append(".medium { background: #fef3c7; color: #92400e; }")
            .append(".low { background: #dcfce7; color: #166534; }")
            .append(".finding { border-bottom: 1px solid #ddd; padding-bottom: 15px; margin-bottom: 15px; }")
            .append("</style></head><body>");

        html.append("<h1>").append(detailed ? "Detailed" : "Executive").append(" Security Summary</h1>");
        html.append("<p><strong>Target:</strong> ")
            .append(scan.getTarget() != null && scan.getTarget().getBaseUrl() != null ? scan.getTarget().getBaseUrl() : "Unknown target")
            .append("</p>");
        html.append("<p><strong>Scan ID:</strong> ").append(scan.getId()).append("</p>");
        html.append("<p><strong>Date:</strong> ").append(scan.getCreatedAt()).append("</p><hr/>");

        html.append("<h2>Vulnerability Overview</h2>");
        html.append("<div class='stats'>")
            .append("<div class='stat-box critical'>Critical<br/>").append(critical).append("</div>")
            .append("<div class='stat-box high'>High<br/>").append(high).append("</div>")
            .append("<div class='stat-box medium'>Medium<br/>").append(medium).append("</div>")
            .append("<div class='stat-box low'>Low<br/>").append(low).append("</div>")
            .append("</div>");

        if (detailed && !findings.isEmpty()) {
            html.append("<h2>Detailed Findings</h2>");
            for (Finding f : findings) {
                html.append("<div class='finding'>");
                html.append("<h3>[").append(f.getSeverity()).append("] ").append(f.getTitle()).append("</h3>");
                html.append("<p><strong>URL:</strong> ").append(f.getAffectedUrl()).append("</p>");
                html.append("<p><strong>Description:</strong> ").append(escapeHtml(resolveFindingDescription(f))).append("</p>");
                if (resolveExploitNarrative(f) != null && !resolveExploitNarrative(f).isBlank()) {
                    html.append("<p><strong>How It Can Be Exploited:</strong> ")
                            .append(escapeHtml(resolveExploitNarrative(f)))
                            .append("</p>");
                }
                if (f.getRemediation() != null && !f.getRemediation().isEmpty()) {
                    html.append("<p><strong>Remediation:</strong> ").append(escapeHtml(f.getRemediation())).append("</p>");
                }
                html.append("</div>");
            }
        } else if (!detailed) {
            html.append("<p><em>This is an executive summary. For technical details, references, and remediation steps, please refer to the Detailed Report or the ScannerX Dashboard.</em></p>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private long countBySeverity(List<Finding> findings, String severity) {
        return findings.stream()
                .filter(finding -> severity.equalsIgnoreCase(String.valueOf(finding.getSeverity())))
                .count();
    }

    private boolean isResolvedStatus(String status) {
        String normalized = String.valueOf(status).toUpperCase(Locale.ROOT);
        return "RESOLVED".equals(normalized) || "PASS".equals(normalized) || "FALSE POSITIVE".equals(normalized);
    }

    private String generateHtmlForSummary(ReportSummaryResponse summary) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='utf-8'/><style>")
                .append("body{font-family:Arial,sans-serif;background:#0f0d0d;color:#f5f5f5;margin:0;padding:32px;}")
                .append(".shell{max-width:1100px;margin:0 auto;}")
                .append(".hero{padding:28px;border:1px solid rgba(255,255,255,.08);border-radius:24px;background:linear-gradient(180deg,rgba(255,255,255,.03),rgba(255,255,255,.01));}")
                .append(".eyebrow{font-size:11px;letter-spacing:.22em;text-transform:uppercase;color:#7be7c0;font-weight:700;}")
                .append("h1{font-size:34px;margin:12px 0 8px;}")
                .append("p.meta{color:#a1a1aa;font-size:14px;margin:0;}")
                .append(".grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:22px;}")
                .append(".card{padding:18px;border:1px solid rgba(255,255,255,.08);border-radius:18px;background:#171313;}")
                .append(".label{font-size:12px;color:#a1a1aa;text-transform:uppercase;letter-spacing:.12em;}")
                .append(".value{font-size:28px;font-weight:700;margin-top:8px;}")
                .append(".section{margin-top:26px;padding:22px;border:1px solid rgba(255,255,255,.08);border-radius:24px;background:#141110;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:18px;font-size:12px;}")
                .append("th,td{padding:10px 12px;border-bottom:1px solid rgba(255,255,255,.08);text-align:left;vertical-align:top;}")
                .append("th{color:#a1a1aa;text-transform:uppercase;font-size:11px;letter-spacing:.08em;}")
                .append(".severity-critical{color:#ff3f7f;}.severity-high{color:#ff8c62;}.severity-medium{color:#f7c84a;}.severity-low{color:#eae7b0;}.severity-info{color:#9ca3af;}")
                .append("</style></head><body><div class='shell'>");

        html.append("<div class='hero'>")
                .append("<div class='eyebrow'>ScannerX Report</div>")
                .append("<h1>").append(escapeHtml(summary.getScopeLabel())).append("</h1>")
                .append("<p class='meta'>Generated ")
                .append(summary.getGeneratedAt() == null ? "" : escapeHtml(summary.getGeneratedAt().format(DATE_TIME_FORMAT)))
                .append("</p>")
                .append("<div class='grid'>")
                .append(statCard("Targets", summary.getTotalTargets()))
                .append(statCard("Scans", summary.getTotalScans()))
                .append(statCard("Findings", summary.getTotalFindings()))
                .append(statCard("Open", summary.getOpenFindings()))
                .append("</div>")
                .append("</div>");

        html.append("<div class='section'><h2>Severity Breakdown</h2><div class='grid'>")
                .append(statCard("Critical", summary.getCriticalFindings()))
                .append(statCard("High", summary.getHighFindings()))
                .append(statCard("Medium", summary.getMediumFindings()))
                .append(statCard("Low", summary.getLowFindings()))
                .append("</div></div>");

        html.append("<div class='section'><h2>Findings</h2><table><thead><tr>")
                .append("<th>Target</th><th>Title</th><th>Severity</th><th>Status</th><th>URL</th><th>Description</th><th>Exploit Narrative</th>")
                .append("</tr></thead><tbody>");

        for (Finding finding : summary.getFindings()) {
            String severityClass = severityClass(String.valueOf(finding.getSeverity()));
            html.append("<tr>")
                    .append("<td>").append(escapeHtml(finding.getTarget() != null ? finding.getTarget().getName() : summary.getTargetName())).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(finding.getTitle()))).append("</td>")
                    .append("<td class='").append(severityClass).append("'>").append(escapeHtml(String.valueOf(finding.getSeverity()))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(finding.getStatus()))).append("</td>")
                    .append("<td>").append(escapeHtml(String.valueOf(finding.getAffectedUrl()))).append("</td>")
                    .append("<td>").append(escapeHtml(resolveFindingDescription(finding))).append("</td>")
                    .append("<td>").append(escapeHtml(resolveExploitNarrative(finding))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></div></div></body></html>");
        return html.toString();
    }

    private String statCard(String label, long value) {
        return "<div class='card'><div class='label'>" + escapeHtml(label) + "</div><div class='value'>" + value + "</div></div>";
    }

    private List<Finding> sanitizeFindings(List<Finding> findings) {
        List<Finding> sanitized = new ArrayList<>();
        for (Finding finding : findings) {
            if (isExecutionNotice(finding)) {
                continue;
            }
            Finding copy = new Finding();
            copy.setId(finding.getId());
            copy.setScan(finding.getScan());
            copy.setTarget(finding.getTarget());
            copy.setCategory(finding.getCategory());
            copy.setTitle(sanitizeFindingTitle(finding.getTitle()));
            copy.setSeverity(finding.getSeverity());
            copy.setStatus(finding.getStatus());
            copy.setAffectedUrl(finding.getAffectedUrl());
            copy.setDescription(sanitizeFindingDescription(finding.getDescription()));
            copy.setAiDescription(sanitizeFindingDescription(finding.getAiDescription()));
            copy.setExploitNarrative(finding.getExploitNarrative());
            copy.setAiEnrichmentStatus(finding.getAiEnrichmentStatus());
            copy.setAiModel(finding.getAiModel());
            copy.setAiPromptFingerprint(finding.getAiPromptFingerprint());
            copy.setAiEnrichedAt(finding.getAiEnrichedAt());
            copy.setAiEnrichmentError(finding.getAiEnrichmentError());
            copy.setRemediation(finding.getRemediation());
            copy.setCweId(finding.getCweId());
            copy.setOwaspCategory(finding.getOwaspCategory());
            copy.setCreatedAt(finding.getCreatedAt());
            copy.setFirstSeenAt(finding.getFirstSeenAt());
            copy.setLastSeenAt(finding.getLastSeenAt());
            sanitized.add(copy);
        }
        return sanitized;
    }

    private boolean isExecutionNotice(Finding finding) {
        return "engine".equalsIgnoreCase(String.valueOf(finding.getToolName()))
                || "execution".equalsIgnoreCase(String.valueOf(finding.getCategory()));
    }

    private String sanitizeFindingTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Security Result";
        }

        return title
                .replaceFirst("(?i)^Nuclei Match:\\s*", "")
                .replaceFirst("(?i)^Discovered Path \\((?:ffuf|Dirb|Gobuster)\\):\\s*", "Discovered Path: ")
                .replaceFirst("(?i)^Nikto Finding$", "Security Check Result")
                .replaceFirst("(?i)^Dalfox XSS:\\s*", "Potential Cross-Site Scripting: ")
                .replaceFirst("(?i)^XSStrike XSS Match$", "Potential Cross-Site Scripting")
                .replaceFirst("(?i)^XSSer Injection Payload$", "Confirmed Cross-Site Scripting")
                .replaceFirst("(?i)^Arachni Detection$", "Security Check Result")
                .trim();
    }

    private String sanitizeFindingDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }

        return description
                .replaceAll("(?i)\\bFfuf discovered path\\b", "Discovered a reachable path")
                .replaceAll("(?i)\\bDirb found path\\b", "Discovered a reachable path")
                .replaceAll("(?i)\\bGobuster found directory\\b", "Discovered a reachable path")
                .replaceAll("(?i)\\bSqlmap detected an injection vector\\b", "Detected an injection vector")
                .replaceAll("(?i)\\bWapiti scan discovered potential vulnerability\\b", "Detected a potential vulnerability")
                .replaceAll("(?i)\\bXSStrike found a potential XSS vector\\b", "Detected a potential cross-site scripting vector")
                .replaceAll("(?i)\\bXSSer confirmed injection success\\b", "Confirmed a cross-site scripting payload")
                .replaceAll("(?i)\\bw3af discovered vulnerability payload\\b", "Detected a vulnerability payload")
                .trim();
    }

    private String resolveFindingDescription(Finding finding) {
        String aiDescription = sanitizeFindingDescription(finding.getAiDescription());
        if (aiDescription != null && !aiDescription.isBlank()) {
            return aiDescription;
        }

        return sanitizeFindingDescription(finding.getDescription());
    }

    private String resolveExploitNarrative(Finding finding) {
        if (finding.getExploitNarrative() == null || finding.getExploitNarrative().isBlank()) {
            return "";
        }

        return finding.getExploitNarrative().trim();
    }

    private String severityClass(String severity) {
        String normalized = severity == null ? "" : severity.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CRITICAL" -> "severity-critical";
            case "HIGH" -> "severity-high";
            case "MEDIUM" -> "severity-medium";
            case "LOW" -> "severity-low";
            default -> "severity-info";
        };
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
