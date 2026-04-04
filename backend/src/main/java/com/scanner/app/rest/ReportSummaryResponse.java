package com.scanner.app.rest;

import com.scanner.app.domain.Finding;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportSummaryResponse {
    private String scopeLabel;
    private Long targetId;
    private String targetName;
    private Long scanId;
    private String scanName;
    private long totalTargets;
    private long totalScans;
    private long totalFindings;
    private long openFindings;
    private long resolvedFindings;
    private long criticalFindings;
    private long highFindings;
    private long mediumFindings;
    private long lowFindings;
    private long informationalFindings;
    private LocalDateTime generatedAt;
    private List<Finding> findings;
}
