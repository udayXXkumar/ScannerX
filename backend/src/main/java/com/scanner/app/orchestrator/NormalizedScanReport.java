package com.scanner.app.orchestrator;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NormalizedScanReport {
    private String target;
    private String tier;
    private String status;
    private Summary summary = new Summary();
    private List<FindingEntry> findings = new ArrayList<>();

    @Data
    public static class Summary {
        private int critical;
        private int high;
        private int medium;
        private int low;
        private int info;
    }

    @Data
    public static class FindingEntry {
        private String type;
        private String severity;
        private String endpoint;
        private String description;
        private String exploitNarrative;
        private String evidence;
        private String source;
    }
}
