package com.scanner.app.rest;

import com.scanner.app.domain.Scan;
import com.scanner.app.domain.User;
import com.scanner.app.orchestrator.NormalizedReportService;
import com.scanner.app.orchestrator.NormalizedScanReport;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final NormalizedReportService normalizedReportService;
    private final ScanRepository scanRepository;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService, NormalizedReportService normalizedReportService, ScanRepository scanRepository, UserRepository userRepository) {
        this.reportService = reportService;
        this.normalizedReportService = normalizedReportService;
        this.scanRepository = scanRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/scans/{scanId}/csv")
    public ResponseEntity<byte[]> getScanCsvReport(@PathVariable Long scanId, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        if (scanRepository.findByIdAndUserId(scanId, currentUser.get().getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String csvContent = reportService.generateCsvReportForScan(scanId);
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "scan_" + scanId + "_report.csv");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    @GetMapping("/scans/{scanId}/json")
    public ResponseEntity<NormalizedScanReport> getScanJsonReport(@PathVariable Long scanId, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        Optional<Scan> scan = scanRepository.findWithContextByIdAndUserId(scanId, currentUser.get().getId());
        if (scan.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(normalizedReportService.readReport(scan.get()));
    }

    @GetMapping(value = "/scans/{scanId}/summary/executive", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getExecutiveSummary(@PathVariable Long scanId, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        if (scanRepository.findByIdAndUserId(scanId, currentUser.get().getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reportService.generateHtmlSummary(scanId, false));
    }

    @GetMapping(value = "/scans/{scanId}/summary/detailed", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDetailedSummary(@PathVariable Long scanId, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        if (scanRepository.findByIdAndUserId(scanId, currentUser.get().getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reportService.generateHtmlSummary(scanId, true));
    }

    @GetMapping("/summary")
    public ResponseEntity<ReportSummaryResponse> getSummary(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        try {
            return ResponseEntity.ok(reportService.buildSummary(currentUser.get().getId(), targetId, scanId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportSummaryCsv(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        try {
            ReportSummaryResponse summary = reportService.buildSummary(currentUser.get().getId(), targetId, scanId);
            byte[] csvBytes = reportService.generateCsvForSummary(summary).getBytes(StandardCharsets.UTF_8);
            String fileName = buildFileName("csv", summary);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csvBytes);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/export/json")
    public ResponseEntity<ReportSummaryResponse> exportSummaryJson(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        try {
            return ResponseEntity.ok(reportService.buildSummary(currentUser.get().getId(), targetId, scanId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportSummaryPdf(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        try {
            ReportSummaryResponse summary = reportService.buildSummary(currentUser.get().getId(), targetId, scanId);
            String fileName = buildFileName("pdf", summary);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(reportService.generatePdfForSummary(summary));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    private String buildFileName(String extension, ReportSummaryResponse summary) {
        String baseName = (summary.getScopeLabel() == null || summary.getScopeLabel().isBlank())
                ? "scannerx-report"
                : summary.getScopeLabel().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return baseName + "." + extension;
    }
}
