package com.scanner.app.orchestrator;

import java.time.Duration;
import java.util.List;

public record TierPlan(
        ScanTier tier,
        Duration hardTimeout,
        List<PlanStage> stages
) {
    public int totalStepCount() {
        return stages.stream().mapToInt(stage -> stage.steps().size()).sum();
    }
}
