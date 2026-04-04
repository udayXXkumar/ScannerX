package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DalfoxExecutor extends AbstractFindingExecutor {

    private final ObjectMapper objectMapper;

    public DalfoxExecutor(FindingService findingService, ToolExecutionService toolExecutionService, ObjectMapper objectMapper) {
        super(findingService, toolExecutionService);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExecutorName() {
        return "dalfox";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "dalfox".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        List<String> candidateUrls = new ArrayList<>(context.getDiscoveredUrls().stream()
                .filter(url -> url.contains("?"))
                .limit(10)
                .toList());

        if (candidateUrls.isEmpty() && context.hasInjectableInputs()) {
            candidateUrls.add(context.getNormalizedTargetUrl());
        }

        if (candidateUrls.isEmpty()) {
            return StepExecutionResult.skipped("Reflected input validation was skipped because no injectable parameters were discovered.");
        }

        boolean anySucceeded = false;
        for (String candidateUrl : candidateUrls) {
            ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                    scan.getId(),
                    List.of("dalfox", "url", candidateUrl, "--format", "json"),
                    List.of("docker", "run", "--rm", "hahwul/dalfox", "url", candidateUrl, "--format", "json"),
                    step.timeout(),
                    line -> {
                        String cleanLine = toolExecutionService.stripAnsi(line).trim();
                        if (!cleanLine.startsWith("{")) {
                            return;
                        }
                        try {
                            JsonNode json = objectMapper.readTree(cleanLine);
                            saveFinding(
                                    scan,
                                    eventPublisher,
                                    context,
                                    "Dalfox",
                                    "Validation",
                                    "Reflected Input Validation",
                                    json.path("severity").asText("HIGH").toUpperCase(),
                                    json.path("url").asText(candidateUrl),
                                    "Detected a potential cross-site scripting vector.",
                                    cleanLine
                            );
                        } catch (Exception ignored) {
                            // Ignore malformed lines.
                        }
                    }
            );

            anySucceeded = anySucceeded || result.exitCode() == 0;
            if (result.timedOut()) {
                return StepExecutionResult.nonFatalFailure("Reflected input validation timed out.", true);
            }
        }

        if (!anySucceeded) {
            return StepExecutionResult.nonFatalFailure("Reflected input validation finished with a non-zero exit code.", false);
        }
        return StepExecutionResult.success("Reflected input validation completed.");
    }
}
