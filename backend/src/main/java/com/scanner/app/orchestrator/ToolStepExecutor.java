package com.scanner.app.orchestrator;

import com.scanner.app.domain.Scan;
import com.scanner.app.websocket.EventPublisher;

public interface ToolStepExecutor {
    String getExecutorName();

    boolean supports(PlanStep step);

    StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception;
}
