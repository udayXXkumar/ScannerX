package com.scanner.app.orchestrator;

import java.util.List;

public record PlanStage(
        int order,
        String label,
        List<PlanStep> steps
) {
}
