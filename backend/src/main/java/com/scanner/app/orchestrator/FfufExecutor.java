package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class FfufExecutor extends AbstractFindingExecutor {

    private final ObjectMapper objectMapper;

    public FfufExecutor(FindingService findingService, ToolExecutionService toolExecutionService, ObjectMapper objectMapper) {
        super(findingService, toolExecutionService);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExecutorName() {
        return "ffuf";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "ffuf-medium".equals(step.key()) || "ffuf-deep".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        String targetUrl = context.getNormalizedTargetUrl();
        String wordlist = resolveWordlist(step);
        long baselineLength = toolExecutionService.detectBaselineLength(targetUrl);
        Set<String> discoveredUrls = new LinkedHashSet<>();
        Set<String> queryParameters = new LinkedHashSet<>();

        List<String> nativeCommand = new ArrayList<>(List.of(
                "ffuf",
                "-u", toolExecutionService.buildFuzzUrl(targetUrl),
                "-w", wordlist,
                "-json",
                "-s",
                "-mc", "all",
                "-rate", step.setting("rate", "50"),
                "-t", step.setting("threads", "15")
        ));

        if (step.boolSetting("recursion", false)) {
            nativeCommand.add("-recursion");
            nativeCommand.add("-recursion-depth");
            nativeCommand.add(step.setting("recursionDepth", "1"));
        }

        if (baselineLength > 0) {
            nativeCommand.add("-fs");
            nativeCommand.add(String.valueOf(baselineLength));
        } else {
            nativeCommand.add("-fc");
            nativeCommand.add("404");
        }

        Duration timeout = step.timeout();
        ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                scan.getId(),
                nativeCommand,
                List.of("docker", "run", "--rm", "ffuf/ffuf", "-u", toolExecutionService.buildFuzzUrl(targetUrl), "-w", "/wordlist.txt", "-json"),
                timeout,
                line -> {
                    String cleanLine = toolExecutionService.stripAnsi(line).trim();
                    if (!cleanLine.startsWith("{")) {
                        return;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(cleanLine);
                        String url = node.path("url").asText("");
                        int status = node.path("status").asInt();
                        if (url.isBlank()) {
                            return;
                        }

                        discoveredUrls.add(url);
                        queryParameters.addAll(extractQueryParameters(url));

                        saveFinding(
                                scan,
                                eventPublisher,
                                context,
                                "ffuf",
                                "Discovery",
                                "Discovered Endpoint",
                                "LOW",
                                url,
                                "Discovered a reachable endpoint responding with HTTP " + status + ".",
                                cleanLine
                        );
                    } catch (Exception ignored) {
                        // Ignore malformed lines.
                    }
                }
        );

        if (result.timedOut()) {
            return StepExecutionResult.nonFatalFailure("Bounded discovery timed out.", true);
        }
        if (result.exitCode() != 0) {
            return StepExecutionResult.nonFatalFailure("Bounded discovery finished with a non-zero exit code.", false);
        }

        return StepExecutionResult.success("Discovery scope expanded.", discoveredUrls, Set.of(), queryParameters, Set.of());
    }

    private String resolveWordlist(PlanStep step) {
        if ("deep".equalsIgnoreCase(step.setting("mode", ""))) {
            return toolExecutionService.resolveWordlist(
                    "/usr/share/seclists/Discovery/Web-Content/raft-small-words.txt",
                    "/usr/share/seclists/Discovery/Web-Content/common.txt",
                    "/usr/share/wordlists/dirb/common.txt"
            );
        }

        return toolExecutionService.resolveWordlist(
                "/usr/share/seclists/Discovery/Web-Content/common.txt",
                "/usr/share/wordlists/dirb/common.txt"
        );
    }

    private Set<String> extractQueryParameters(String url) {
        Set<String> parameters = new LinkedHashSet<>();
        try {
            String query = URI.create(url).getQuery();
            if (query == null || query.isBlank()) {
                return parameters;
            }
            for (String segment : query.split("&")) {
                int separator = segment.indexOf('=');
                parameters.add(separator >= 0 ? segment.substring(0, separator) : segment);
            }
        } catch (Exception ignored) {
            // Ignore malformed URLs.
        }
        return parameters;
    }
}
