package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingEnrichmentService;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

@Component
public class DalfoxExecutor extends AbstractFindingExecutor {

    private final ObjectMapper objectMapper;

    public DalfoxExecutor(FindingService findingService, ToolExecutionService toolExecutionService, ObjectMapper objectMapper, FindingEnrichmentService findingEnrichmentService) {
        super(findingService, toolExecutionService, findingEnrichmentService);
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
                .filter(url -> url != null && url.contains("?") && !isSocketIoTransportUrl(url))
                .distinct()
                .limit(Math.max(1, step.intSetting("maxTargets", 10)))
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
                            String findingType = json.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                            String message = firstNonBlank(
                                    json.path("message_str").asText(null),
                                    json.path("message").asText(null),
                                    json.path("type_description").asText(null)
                            );

                            // Dalfox also emits reflection and informational records. Only persist
                            // its exploitable (V) claims and explicit AST analysis candidates;
                            // plain reflection is not proof of XSS. Older versions may omit
                            // `type`, so require the explicit triggered-payload message then.
                            boolean verified = "V".equals(findingType)
                                    || message.toLowerCase(Locale.ROOT).contains("triggered xss payload");
                            boolean astCandidate = "ast".equalsIgnoreCase(json.path("detection_method").asText(""));
                            if (!verified && !astCandidate) {
                                return;
                            }

                            String severity = verified
                                    ? normalizeSeverity(json.path("severity").asText("HIGH"), "HIGH")
                                    : "LOW";
                            String title = verified ? "Reflected Cross-Site Scripting" : "Potential DOM Cross-Site Scripting";
                            String description = astCandidate
                                    ? "Dalfox identified a possible DOM XSS sink by static analysis; manual validation is required."
                                    : firstNonBlank(message, "Dalfox verified an exploitable reflected XSS payload.");
                            String affectedUrl = firstNonBlank(json.path("url").asText(null), candidateUrl);
                            saveFinding(
                                    scan,
                                    eventPublisher,
                                    context,
                                    "Dalfox",
                                    "Validation",
                                    title,
                                    severity,
                                    affectedUrl,
                                    description,
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

    static boolean isSocketIoTransportUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).contains("/socket.io/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeSeverity(String value, String fallback) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "CRITICAL";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            case "LOW" -> "LOW";
            case "INFO", "INFORMATIONAL" -> "INFO";
            default -> fallback;
        };
    }
}
