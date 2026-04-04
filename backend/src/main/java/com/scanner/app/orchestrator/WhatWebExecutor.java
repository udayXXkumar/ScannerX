package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class WhatWebExecutor extends AbstractFindingExecutor {

    private final ObjectMapper objectMapper;

    public WhatWebExecutor(FindingService findingService, ToolExecutionService toolExecutionService, ObjectMapper objectMapper) {
        super(findingService, toolExecutionService);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExecutorName() {
        return "whatweb";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "whatweb".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        String targetUrl = context.getNormalizedTargetUrl();
        StringBuilder output = new StringBuilder();

        ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                scan.getId(),
                List.of("whatweb", "--log-json=-", "-q", "--color=never", targetUrl),
                List.of("docker", "run", "--rm", "uzyexe/whatweb", "--log-json=/dev/stdout", "-q", "--color=never", targetUrl),
                step.timeout(),
                line -> {
                    String cleanLine = toolExecutionService.stripAnsi(line);
                    if (!cleanLine.isBlank()) {
                        output.append(cleanLine).append(System.lineSeparator());
                    }
                }
        );

        if (!output.isEmpty() && output.toString().trim().startsWith("[")) {
            JsonNode rootNode = objectMapper.readTree(output.toString());
            if (rootNode.isArray() && !rootNode.isEmpty()) {
                JsonNode plugins = rootNode.get(0).path("plugins");
                if (plugins != null && plugins.isObject()) {
                    String technologies = StreamSupport.stream(
                                    Spliterators.spliteratorUnknownSize(plugins.fieldNames(), Spliterator.ORDERED),
                                    false
                            )
                            .collect(Collectors.joining(", "));

                    saveFinding(
                            scan,
                            eventPublisher,
                            context,
                            "WhatWeb",
                            "Baseline",
                            "Fingerprinting Summary",
                            "INFO",
                            targetUrl,
                            technologies.isBlank() ? "Technology fingerprinting completed." : "Detected technologies: " + technologies,
                            technologies
                    );
                }
            }
        }

        if (result.timedOut()) {
            return StepExecutionResult.nonFatalFailure("Technology fingerprinting timed out.", true);
        }
        if (result.exitCode() != 0) {
            return StepExecutionResult.nonFatalFailure("Technology fingerprinting finished with a non-zero exit code.", false);
        }
        return StepExecutionResult.success("Technology fingerprinting completed.");
    }
}
