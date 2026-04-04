package com.scanner.app.rest;

import com.scanner.app.domain.Finding;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardSummary {
    private long totalTargets;
    private long totalScans;
    private long totalFindings;
    private int riskScore;
    
    private List<FindingTrend> findingsTrend;
    private List<Finding> latestFindings;
    
    @Data
    public static class FindingTrend {
        private String name;
        private long findings;
        
        public FindingTrend(String name, long findings) {
            this.name = name;
            this.findings = findings;
        }
    }
}
