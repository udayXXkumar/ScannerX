package com.scanner.app.orchestrator;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;

import java.time.LocalDateTime;

abstract class AbstractFindingExecutor implements ToolStepExecutor {

    protected final FindingService findingService;
    protected final ToolExecutionService toolExecutionService;

    protected AbstractFindingExecutor(FindingService findingService, ToolExecutionService toolExecutionService) {
        this.findingService = findingService;
        this.toolExecutionService = toolExecutionService;
    }

    protected Finding saveFinding(
            Scan scan,
            EventPublisher eventPublisher,
            String toolName,
            String category,
            String title,
            String severity,
            String affectedUrl,
            String description,
            String evidence
    ) {
        return saveFinding(scan, eventPublisher, null, toolName, category, title, severity, affectedUrl, description, evidence);
    }

    protected Finding saveFinding(
            Scan scan,
            EventPublisher eventPublisher,
            ScanExecutionContext context,
            String toolName,
            String category,
            String title,
            String severity,
            String affectedUrl,
            String description,
            String evidence
    ) {
        Finding finding = new Finding();
        finding.setScan(scan);
        finding.setTarget(scan.getTarget());
        finding.setToolName(toolName);
        finding.setCategory(category);
        finding.setTitle(title);
        finding.setSeverity(severity);
        finding.setStatus("OPEN");
        finding.setAffectedUrl(affectedUrl);
        finding.setDescription(description);
        finding.setEvidenceData(evidence);
        finding.setCreatedAt(LocalDateTime.now());
        finding = findingService.saveOrUpdateFinding(finding);
        if (context != null) {
            context.markForwardProgress();
        }
        eventPublisher.publishScanEvent(scan.getId(), "FINDING_FOUND", finding);
        return finding;
    }
}
