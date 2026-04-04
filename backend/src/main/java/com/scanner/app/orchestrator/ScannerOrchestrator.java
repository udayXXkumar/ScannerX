package com.scanner.app.orchestrator;

import org.springframework.stereotype.Service;

@Service
public class ScannerOrchestrator {

    private final TierScanEngine tierScanEngine;

    public ScannerOrchestrator(TierScanEngine tierScanEngine) {
        this.tierScanEngine = tierScanEngine;
    }

    public void runScan(Long scanId) {
        tierScanEngine.runScan(scanId);
    }
}
