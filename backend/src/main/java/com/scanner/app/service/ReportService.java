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
import java.time.Duration;
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
        sb.append("Type,Severity,AI Severity,Priority Score,Is Duplicate,Endpoint,Description,Exploit Narrative,Evidence,Source\n");

        for (NormalizedScanReport.FindingEntry finding : report.getFindings()) {
            sb.append(escapeCsv(finding.getType())).append(",");
            sb.append(escapeCsv(finding.getSeverity())).append(",");
            sb.append(escapeCsv(finding.getAiSeverity())).append(",");
            sb.append(finding.getAiPriorityScore() != null ? finding.getAiPriorityScore() : "").append(",");
            sb.append(finding.isDuplicate() ? "Yes" : "No").append(",");
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
        Scan selectedScan = null;
        if (scanId != null) {
            Scan requestedScan = scanRepository.findWithContextByIdAndUserId(scanId, userId)
                    .filter(scan -> "COMPLETED".equalsIgnoreCase(scan.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Completed scan not found."));
            selectedScan = requestedScan;
            completedScans = completedScans.stream().filter(scan -> scan.getId().equals(requestedScan.getId())).toList();
            targetId = requestedScan.getTarget().getId();
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
        response.setScanName(selectedScan != null && selectedScan.getName() != null && !selectedScan.getName().isBlank()
                ? selectedScan.getName().trim()
                : resolvedTargetName);
        response.setScopeLabel(selectedScan != null
                ? response.getTargetName() + " · Scan #" + selectedScan.getId()
                : targetId != null ? response.getTargetName() + " · All completed runs" : "All targets");
        if (selectedScan == null && firstScan != null && firstScan.getTarget() != null) {
            response.setTargetUrl(firstScan.getTarget().getBaseUrl());
        }
        if (selectedScan != null) {
            response.setScanTier(selectedScan.getTier() == null ? selectedScan.getProfileType() : selectedScan.getTier());
            response.setScanStatus(selectedScan.getStatus());
            response.setTargetUrl(selectedScan.getTarget() == null ? null : selectedScan.getTarget().getBaseUrl());
            response.setScanStartedAt(selectedScan.getStartedAt());
            response.setScanCompletedAt(selectedScan.getCompletedAt());
            if (selectedScan.getStartedAt() != null && selectedScan.getCompletedAt() != null) {
                response.setScanDurationSeconds(Duration.between(selectedScan.getStartedAt(), selectedScan.getCompletedAt()).toSeconds());
            }
        }

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
        sb.append("ID,Target Name,Category,Title,Severity,AI Severity,Priority Score,Status,Affected URL,Is Duplicate,CWE,OWASP,Created At,Description,Exploit Narrative\n");

        for (Finding finding : summary.getFindings()) {
            String targetName = finding.getTarget() != null ? finding.getTarget().getName() : summary.getTargetName();
            sb.append(escapeCsv(String.valueOf(finding.getId()))).append(",");
            sb.append(escapeCsv(targetName)).append(",");
            sb.append(escapeCsv(finding.getCategory())).append(",");
            sb.append(escapeCsv(finding.getTitle())).append(",");
            sb.append(escapeCsv(finding.getSeverity())).append(",");
            sb.append(escapeCsv(finding.getAiSeverity())).append(",");
            sb.append(finding.getAiPriorityScore() != null ? finding.getAiPriorityScore() : "").append(",");
            sb.append(escapeCsv(finding.getStatus())).append(",");
            sb.append(escapeCsv(finding.getAffectedUrl())).append(",");
            sb.append(finding.getAiDuplicateOfId() != null ? "Yes" : "No").append(",");
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
        List<Finding> findings = summary.getFindings() == null ? List.of() : summary.getFindings().stream()
                .sorted((left, right) -> {
                    int duplicateOrder = Boolean.compare(left.getAiDuplicateOfId() != null, right.getAiDuplicateOfId() != null);
                    if (duplicateOrder != 0) return duplicateOrder;
                    int severityOrder = Integer.compare(severityRank(right.getSeverity()), severityRank(left.getSeverity()));
                    if (severityOrder != 0) return severityOrder;
                    return Integer.compare(priority(right), priority(left));
                })
                .toList();

        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='utf-8'/><style>")
                .append("@page{size:A4;margin:16mm 15mm 18mm;}*{box-sizing:border-box;}")
                .append("body{font-family:Arial,Helvetica,sans-serif;color:#16212b;background:#fff;font-size:10pt;line-height:1.45;margin:0;}")
                .append("h1,h2,h3,p{margin-top:0;}h1{font-size:30pt;line-height:1.08;letter-spacing:-.6pt;margin:27mm 0 8mm;color:#102a35;}")
                .append("h2{font-size:17pt;line-height:1.2;margin:0 0 12pt;color:#132f3a;}h3{font-size:12.5pt;margin:0 0 5pt;color:#142e38;}")
                .append("p{margin:0 0 8pt;}table{width:100%;border-collapse:collapse;table-layout:fixed;}td,th{vertical-align:top;}")
                .append(".cover{height:258mm;position:relative;page-break-after:always;padding:2mm 0;}")
                .append(".brandbar{border-bottom:2px solid #54c6a1;padding:0 0 11pt;}")
                .append(".brandbar table{margin:0;} .brandmark{display:inline-block;background:#102a35;color:#fff;font-size:11pt;font-weight:bold;text-align:center;padding:7pt 8pt;border-radius:7pt;letter-spacing:-.5pt;}")
                .append(".brandname{font-size:16pt;font-weight:800;letter-spacing:1.1pt;color:#102a35;padding-left:8pt;vertical-align:middle;}")
                .append(".brandtag{font-size:8pt;letter-spacing:1.2pt;color:#526773;text-align:right;text-transform:uppercase;vertical-align:middle;}")
                .append(".cover-kicker,.section-kicker{font-size:8pt;font-weight:bold;letter-spacing:1.6pt;color:#168568;text-transform:uppercase;margin-bottom:8pt;}")
                .append(".cover-title{font-size:31pt;color:#102a35;font-weight:700;line-height:1.08;margin:30mm 0 8pt;}")
                .append(".cover-target{font-size:16pt;color:#294853;font-weight:600;overflow-wrap:anywhere;margin-bottom:7pt;}")
                .append(".cover-scope{font-size:10pt;color:#647681;}")
                .append(".cover-rule{height:4pt;background:#54c6a1;width:44mm;margin:20mm 0 12mm;}")
                .append(".cover-meta{border-top:1px solid #d9e3e4;border-bottom:1px solid #d9e3e4;margin-top:15mm;}")
                .append(".cover-meta td{width:25%;padding:11pt 8pt 12pt 0;border-right:1px solid #d9e3e4;}")
                .append(".cover-meta td:last-child{border-right:0;padding-left:8pt;}.meta-label{font-size:7.5pt;color:#72828b;letter-spacing:.8pt;text-transform:uppercase;}")
                .append(".meta-value{font-size:11pt;color:#182e38;font-weight:bold;margin-top:4pt;overflow-wrap:anywhere;}")
                .append(".cover-footer{position:absolute;bottom:0;left:0;right:0;border-top:1px solid #d9e3e4;padding-top:8pt;color:#71818a;font-size:8pt;}")
                .append(".section{margin:0 0 18pt;page-break-inside:avoid;}.section-title{border-left:4px solid #54c6a1;padding-left:10pt;margin-bottom:10pt;}")
                .append(".metrics{border-spacing:6pt 0;margin:0 -6pt 14pt;width:calc(100% + 12pt);}.metric{background:#f3f7f6;border:1px solid #dce8e5;padding:10pt 11pt;width:25%;}")
                .append(".metric-label{font-size:7.3pt;letter-spacing:.8pt;text-transform:uppercase;color:#647780;}.metric-value{font-size:21pt;line-height:1.15;color:#17313b;font-weight:bold;margin-top:4pt;}")
                .append(".severity-strip{border-spacing:3pt 0;margin:0 -3pt 15pt;width:calc(100% + 6pt);}.severity-cell{padding:8pt 7pt;background:#f7f9fa;border-top:3px solid #9aa9b0;width:20%;}")
                .append(".severity-cell .metric-value{font-size:17pt;}.sev-critical{border-color:#d9485f;}.sev-high{border-color:#e87547;}.sev-medium{border-color:#d9a82d;}.sev-low{border-color:#62a989;}.sev-info{border-color:#6c91a6;}")
                .append(".overview{background:#f4f8f7;border:1px solid #dce8e5;padding:13pt 14pt;margin-bottom:15pt;}.risk-label{font-size:18pt;font-weight:bold;color:#17313b;margin:1pt 0 5pt;}")
                .append(".muted{color:#60727c;}.small{font-size:8.5pt;}.table{font-size:8.2pt;margin:0 0 16pt;}.table th{background:#edf3f3;color:#425963;font-size:7pt;text-transform:uppercase;letter-spacing:.7pt;padding:7pt 6pt;border-bottom:1px solid #d7e1e2;text-align:left;}")
                .append(".table td{padding:7pt 6pt;border-bottom:1px solid #e4eaeb;overflow-wrap:anywhere;}.rank{color:#74848b;font-size:8pt;white-space:nowrap;}.finding-title{font-weight:bold;color:#19323b;}.endpoint{color:#617780;font-size:7.5pt;overflow-wrap:anywhere;margin-top:3pt;}")
                .append(".severity-critical{color:#b8324c;font-weight:bold;}.severity-high{color:#c65c34;font-weight:bold;}.severity-medium{color:#9b7413;font-weight:bold;}.severity-low{color:#337c5d;font-weight:bold;}.severity-info{color:#55798a;font-weight:bold;}")
                .append(".status{font-size:7.3pt;color:#3c6470;text-transform:uppercase;font-weight:bold;}.priority{font-weight:bold;color:#286f60;white-space:nowrap;}")
                .append(".details-start{page-break-before:always;}.finding-detail{border:1px solid #dbe4e5;border-left:4px solid #54c6a1;margin:0 0 13pt;padding:12pt 13pt;page-break-inside:avoid;}")
                .append(".detail-header{margin:0 0 8pt;}.detail-header td:first-child{width:72%;}.detail-header td:last-child{text-align:right;width:28%;}")
                .append(".detail-id{font-size:7.5pt;color:#71818a;letter-spacing:.5pt;margin-bottom:4pt;}.pill{display:inline-block;padding:3pt 6pt;border:1px solid #d5e1e2;background:#f5f8f8;color:#304952;font-size:7pt;font-weight:bold;text-transform:uppercase;}")
                .append(".detail-section{margin-top:8pt;}.detail-label{font-size:7pt;font-weight:bold;letter-spacing:.7pt;text-transform:uppercase;color:#536a73;margin-bottom:3pt;}.detail-copy{font-size:8.5pt;color:#273c44;white-space:pre-wrap;overflow-wrap:anywhere;}")
                .append(".evidence{background:#f5f7f8;border:1px solid #e1e7e8;padding:7pt 8pt;font-size:8pt;color:#334b55;white-space:pre-wrap;overflow-wrap:anywhere;}")
                .append(".classification{border-top:1px solid #e1e7e8;margin-top:8pt;padding-top:7pt;color:#637780;font-size:7.8pt;}.empty{padding:18pt;background:#f5f8f8;color:#657780;border:1px solid #e1e7e8;}")
                .append("</style></head><body>");

        String generatedAt = summary.getGeneratedAt() == null ? "" : summary.getGeneratedAt().format(DATE_TIME_FORMAT);
        String scope = textOr(summary.getScopeLabel(), "Security assessment");
        String target = textOr(summary.getTargetUrl(), summary.getTargetName());
        String scanType = textOr(summary.getScanTier(), summary.getScanId() == null
                ? (summary.getTotalScans() > 1 ? "Multiple scans" : "Portfolio")
                : "Security scan");
        String scanPeriod = summary.getScanStartedAt() == null ? "Completed assessment" : summary.getScanStartedAt().format(DATE_TIME_FORMAT);
        String risk = overallRisk(summary);

        html.append("<div class='cover'>")
                .append("<div class='brandbar'><table><tr><td><span class='brandmark'>SX</span><span class='brandname'>SCANNERX</span></td><td class='brandtag'>Application security<br/>assessment</td></tr></table></div>")
                .append("<div class='cover-kicker' style='margin-top:25mm'>Security assessment</div>")
                .append("<div class='cover-title'>Vulnerability<br/>assessment report</div>")
                .append("<div class='cover-target'>").append(escapeHtml(target)).append("</div>")
                .append("<div class='cover-scope'>").append(escapeHtml(scope)).append("</div>")
                .append("<div class='cover-rule'></div>")
                .append("<div class='section-kicker'>Assessment at a glance</div>")
                .append("<div class='risk-label'>Overall risk: ").append(escapeHtml(risk)).append("</div>")
                .append("<p class='muted'>ScannerX recorded ").append(summary.getTotalFindings()).append(" findings across ")
                .append(summary.getTotalTargets()).append(summary.getTotalTargets() == 1 ? " target" : " targets")
                .append(". Findings are listed with scanner severity preserved; AI severity and priority are shown as supplementary analysis.</p>")
                .append("<table class='cover-meta'><tr>")
                .append(coverMetaCell("Scan profile", scanType))
                .append(coverMetaCell("Scans", String.valueOf(summary.getTotalScans())))
                .append(coverMetaCell("Generated", generatedAt))
                .append(coverMetaCell("Duration", formatDuration(summary.getScanDurationSeconds())))
                .append("</tr></table>")
                .append("<div class='cover-footer'>SCANNERX  ·  CONFIDENTIAL SECURITY REPORT</div>")
                .append("</div>");

        html.append("<div class='section'>")
                .append("<div class='section-title'><div class='section-kicker'>01 · Executive overview</div><h2>Assessment summary</h2></div>")
                .append("<div class='overview'><div class='meta-label'>Overall risk rating</div><div class='risk-label'>").append(escapeHtml(risk)).append("</div>")
                .append("<p class='small muted'>Rating reflects the highest raw scanner severity recorded in this report. AI suggestions are supplemental and do not replace scanner evidence.</p></div>")
                .append("<table class='metrics'><tr>")
                .append(metricCell("Findings", summary.getTotalFindings()))
                .append(metricCell("Open", summary.getOpenFindings()))
                .append(metricCell("Resolved", summary.getResolvedFindings()))
                .append(metricCell("Targets", summary.getTotalTargets()))
                .append("</tr></table>")
                .append("<table class='severity-strip'><tr>")
                .append(severityCell("Critical", summary.getCriticalFindings(), "sev-critical"))
                .append(severityCell("High", summary.getHighFindings(), "sev-high"))
                .append(severityCell("Medium", summary.getMediumFindings(), "sev-medium"))
                .append(severityCell("Low", summary.getLowFindings(), "sev-low"))
                .append(severityCell("Info", summary.getInformationalFindings(), "sev-info"))
                .append("</tr></table>")
                .append("<table class='metrics'><tr>")
                .append(metricCell("Scan profile", scanType))
                .append(metricCell("Scan status", textOr(summary.getScanStatus(), "Completed")))
                .append(metricCell("Started", scanPeriod))
                .append(metricCell("Finished", summary.getScanCompletedAt() == null ? "—" : summary.getScanCompletedAt().format(DATE_TIME_FORMAT)))
                .append("</tr></table>")
                .append("</div>");

        html.append("<div class='section'>")
                .append("<div class='section-title'><div class='section-kicker'>02 · Findings register</div><h2>Prioritized findings</h2></div>")
                .append("<p class='small muted'>Ordered by scanner severity, then AI priority. Duplicate findings remain listed and are marked for analyst review.</p>")
                .append("<table class='table'><thead><tr><th style='width:7%'>ID</th><th style='width:39%'>Finding and endpoint</th><th style='width:14%'>Severity</th><th style='width:12%'>AI priority</th><th style='width:13%'>Status</th><th style='width:15%'>Target</th></tr></thead><tbody>");
        for (Finding finding : findings) {
            html.append(renderFindingRegisterRow(finding, summary));
        }
        if (findings.isEmpty()) {
            html.append("<tr><td colspan='6' class='empty'>No findings were recorded for this report scope.</td></tr>");
        }
        html.append("</tbody></table></div>")
                .append("<div class='details-start'>")
                .append("<div class='section-title'><div class='section-kicker'>03 · Technical appendix</div><h2>Finding details</h2></div>");

        if (findings.isEmpty()) {
            html.append("<div class='empty'>There are no finding details to display.</div>");
        } else {
            for (Finding finding : findings) {
                html.append(renderFindingDetail(finding, summary));
            }
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    private String coverMetaCell(String label, String value) {
        return "<td><div class='meta-label'>" + escapeHtml(label) + "</div><div class='meta-value'>" + escapeHtml(textOr(value, "—")) + "</div></td>";
    }

    private String metricCell(String label, long value) {
        return "<td class='metric'><div class='metric-label'>" + escapeHtml(label) + "</div><div class='metric-value'>" + value + "</div></td>";
    }

    private String metricCell(String label, String value) {
        return "<td class='metric'><div class='metric-label'>" + escapeHtml(label) + "</div><div class='metric-value' style='font-size:11pt'>" + escapeHtml(textOr(value, "—")) + "</div></td>";
    }

    private String severityCell(String label, long value, String styleClass) {
        return "<td class='severity-cell " + styleClass + "'><div class='metric-label'>" + escapeHtml(label) + "</div><div class='metric-value'>" + value + "</div></td>";
    }

    private String renderFindingRegisterRow(Finding finding, ReportSummaryResponse summary) {
        String severity = textOr(finding.getSeverity(), "INFO");
        String title = textOr(finding.getTitle(), "Security result");
        String endpoint = textOr(finding.getAffectedUrl(), summary.getTargetUrl());
        String target = finding.getTarget() == null ? summary.getTargetName() : finding.getTarget().getName();
        String aiPriority = finding.getAiPriorityScore() == null ? "—" : finding.getAiPriorityScore() + "/10";
        if (finding.getAiDuplicateOfId() != null) aiPriority += " · Duplicate";
        return "<tr><td class='rank'>#" + (finding.getId() == null ? "—" : finding.getId()) + "</td>"
                + "<td><div class='finding-title'>" + escapeHtml(title) + "</div><div class='endpoint'>" + escapeHtml(textOr(endpoint, "Endpoint not recorded")) + "</div></td>"
                + "<td class='" + severityClass(severity) + "'>" + escapeHtml(severity) + "</td>"
                + "<td class='priority'>" + escapeHtml(aiPriority) + "</td>"
                + "<td class='status'>" + escapeHtml(textOr(finding.getStatus(), "OPEN")) + "</td>"
                + "<td>" + escapeHtml(textOr(target, "—")) + "</td></tr>";
    }

    private String renderFindingDetail(Finding finding, ReportSummaryResponse summary) {
        String severity = textOr(finding.getSeverity(), "INFO");
        String endpoint = textOr(finding.getAffectedUrl(), summary.getTargetUrl());
        String target = finding.getTarget() == null ? summary.getTargetName() : finding.getTarget().getName();
        String description = resolveFindingDescription(finding);
        String evidence = finding.getEvidenceData();
        String remediation = finding.getRemediation();
        String exploitNarrative = resolveExploitNarrative(finding);

        StringBuilder detail = new StringBuilder("<div class='finding-detail'><table class='detail-header'><tr><td>")
                .append("<div class='detail-id'>FINDING #").append(finding.getId() == null ? "—" : finding.getId()).append("</div>")
                .append("<h3>").append(escapeHtml(textOr(finding.getTitle(), "Security result"))).append("</h3>")
                .append("<div class='endpoint'>").append(escapeHtml(textOr(endpoint, "Endpoint not recorded"))).append("</div></td><td>")
                .append("<span class='pill ").append(severityClass(severity)).append("'>").append(escapeHtml(severity)).append("</span>");
        if (finding.getAiSeverity() != null && !finding.getAiSeverity().isBlank()) {
            detail.append("<div class='detail-id' style='margin-top:5pt'>AI assessment: ").append(escapeHtml(finding.getAiSeverity())).append("</div>");
        }
        if (finding.getAiPriorityScore() != null) {
            detail.append("<div class='priority' style='margin-top:4pt'>Priority ").append(finding.getAiPriorityScore()).append("/10</div>");
        }
        detail.append("</td></tr></table>")
                .append("<div class='detail-id'>TARGET · ").append(escapeHtml(textOr(target, "—")))
                .append(" · STATUS · ").append(escapeHtml(textOr(finding.getStatus(), "OPEN"))).append("</div>");

        if (finding.getAiDuplicateOfId() != null) {
            detail.append("<div class='detail-section'><span class='pill'>Semantic duplicate of finding #")
                    .append(finding.getAiDuplicateOfId()).append("</span></div>");
        }
        if (description != null && !description.isBlank()) {
            detail.append(detailSection("Finding summary", description));
        }
        if (finding.getAiSeverityReason() != null && !finding.getAiSeverityReason().isBlank()) {
            detail.append(detailSection("AI severity rationale", finding.getAiSeverityReason()));
        }
        if (finding.getAiPriorityReason() != null && !finding.getAiPriorityReason().isBlank()) {
            detail.append(detailSection("AI priority rationale", finding.getAiPriorityReason()));
        }
        if (evidence != null && !evidence.isBlank()) {
            detail.append("<div class='detail-section'><div class='detail-label'>Evidence</div><div class='evidence'>")
                    .append(escapeHtml(evidence)).append("</div></div>");
        }
        if (remediation != null && !remediation.isBlank()) {
            detail.append(detailSection("Recommended remediation", remediation));
        }
        if (exploitNarrative != null && !exploitNarrative.isBlank()) {
            detail.append(detailSection("Attacker context and defensive guidance", exploitNarrative));
        }
        if ((finding.getCweId() != null && !finding.getCweId().isBlank())
                || (finding.getOwaspCategory() != null && !finding.getOwaspCategory().isBlank())) {
            detail.append("<div class='classification'><strong>Classification:</strong> ")
                    .append(escapeHtml(textOr(finding.getCweId(), "")));
            if (finding.getCweId() != null && !finding.getCweId().isBlank()
                    && finding.getOwaspCategory() != null && !finding.getOwaspCategory().isBlank()) {
                detail.append(" · ");
            }
            detail.append(escapeHtml(textOr(finding.getOwaspCategory(), ""))).append("</div>");
        }
        return detail.append("</div>").toString();
    }

    private String detailSection(String label, String content) {
        return "<div class='detail-section'><div class='detail-label'>" + escapeHtml(label)
                + "</div><div class='detail-copy'>" + escapeHtml(content) + "</div></div>";
    }

    private int severityRank(String severity) {
        return switch (String.valueOf(severity).toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM", "MODERATE" -> 3;
            case "LOW" -> 2;
            default -> 1;
        };
    }

    private int priority(Finding finding) {
        return finding.getAiPriorityScore() == null ? 0 : finding.getAiPriorityScore();
    }

    private String overallRisk(ReportSummaryResponse summary) {
        if (summary.getCriticalFindings() > 0) return "Critical";
        if (summary.getHighFindings() > 0) return "High";
        if (summary.getMediumFindings() > 0) return "Moderate";
        if (summary.getLowFindings() > 0) return "Low";
        if (summary.getInformationalFindings() > 0) return "Informational";
        return "No findings";
    }

    private String formatDuration(Long seconds) {
        if (seconds == null || seconds < 0) return "—";
        if (seconds < 60) return seconds + " sec";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return remainingSeconds == 0 ? minutes + " min" : minutes + " min " + remainingSeconds + " sec";
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
            copy.setEvidenceData(finding.getEvidenceData());
            copy.setAiDescription(sanitizeFindingDescription(finding.getAiDescription()));
            copy.setExploitNarrative(finding.getExploitNarrative());
            copy.setAiEnrichmentStatus(finding.getAiEnrichmentStatus());
            copy.setAiModel(finding.getAiModel());
            copy.setAiPromptFingerprint(finding.getAiPromptFingerprint());
            copy.setAiEnrichedAt(finding.getAiEnrichedAt());
            copy.setAiEnrichmentError(finding.getAiEnrichmentError());
            copy.setAiSeverity(finding.getAiSeverity());
            copy.setAiSeverityReason(finding.getAiSeverityReason());
            copy.setAiPriorityScore(finding.getAiPriorityScore());
            copy.setAiPriorityReason(finding.getAiPriorityReason());
            copy.setAiDuplicateOfId(finding.getAiDuplicateOfId());
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
