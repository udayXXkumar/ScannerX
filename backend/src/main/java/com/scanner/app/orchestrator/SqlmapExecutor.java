package com.scanner.app.orchestrator;

import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SqlmapExecutor extends AbstractFindingExecutor {

    public SqlmapExecutor(FindingService findingService, ToolExecutionService toolExecutionService) {
        super(findingService, toolExecutionService);
    }

    @Override
    public String getExecutorName() {
        return "sqlmap";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "sqlmap".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        List<String> candidateUrls = new ArrayList<>(context.getDiscoveredUrls().stream()
                .filter(url -> url.contains("?"))
                .limit(step.intSetting("batchCount", 5))
                .toList());
        if (candidateUrls.isEmpty() && toolExecutionService.hasQueryParameters(context.getNormalizedTargetUrl())) {
            candidateUrls.add(context.getNormalizedTargetUrl());
        }

        if (candidateUrls.isEmpty() && !context.hasInjectableInputs()) {
            return StepExecutionResult.skipped("Injection validation was skipped because no testable parameters were discovered.");
        }

        if (candidateUrls.isEmpty()) {
            return StepExecutionResult.skipped("Injection validation was skipped because no query-style parameters were available.");
        }

        boolean anySucceeded = false;
        for (String candidateUrl : candidateUrls) {
            ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                    scan.getId(),
                    List.of("sqlmap", "-u", candidateUrl, "--batch", "--level=2", "--risk=1", "--flush-session"),
                    List.of("docker", "run", "--rm", "sqlmap/sqlmap", "-u", candidateUrl, "--batch", "--level=2", "--risk=1"),
                    step.timeout(),
                    line -> {
                        String cleanLine = toolExecutionService.stripAnsi(line).trim();
                        if (cleanLine.contains("is vulnerable") || cleanLine.contains("appears to be injectable")) {
                            saveFinding(
                                    scan,
                                    eventPublisher,
                                    context,
                                    "sqlmap",
                                    "Validation",
                                    "Confirmed Injection Vector",
                                    "CRITICAL",
                                    candidateUrl,
                                    "A confirmed injection vector was identified.",
                                    cleanLine
                            );
                        }
                    }
            );

            anySucceeded = anySucceeded || result.exitCode() == 0;
            if (result.timedOut()) {
                return StepExecutionResult.nonFatalFailure("Injection validation timed out.", true);
            }
        }

        if (!anySucceeded) {
            return StepExecutionResult.nonFatalFailure("Injection validation finished with a non-zero exit code.", false);
        }
        return StepExecutionResult.success("Injection validation completed.");
    }
}
