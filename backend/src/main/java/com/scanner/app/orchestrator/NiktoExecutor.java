package com.scanner.app.orchestrator;

import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NiktoExecutor extends AbstractFindingExecutor {

    public NiktoExecutor(FindingService findingService, ToolExecutionService toolExecutionService) {
        super(findingService, toolExecutionService);
    }

    @Override
    public String getExecutorName() {
        return "nikto";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "nikto".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        String targetUrl = context.getNormalizedTargetUrl();
        ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                scan.getId(),
                List.of("nikto", "-h", targetUrl, "-nointeractive", "-ask", "no"),
                List.of("docker", "run", "--rm", "sullo/nikto", "-h", targetUrl, "-nointeractive", "-ask", "no"),
                step.timeout(),
                line -> {
                    String cleanLine = toolExecutionService.stripAnsi(line).trim();
                    if (!shouldCreateFinding(cleanLine)) {
                        return;
                    }

                    saveFinding(
                            scan,
                            eventPublisher,
                            context,
                            "Nikto",
                            "Passive",
                            buildFindingTitle(cleanLine),
                            null,
                            extractAffectedUrl(targetUrl, cleanLine),
                            cleanLine.replaceFirst("^\\+\\s*", ""),
                            cleanLine
                    );
                }
        );

        if (result.timedOut()) {
            return StepExecutionResult.nonFatalFailure("Server checks timed out.", true);
        }
        if (result.exitCode() != 0) {
            return StepExecutionResult.nonFatalFailure("Server checks finished with a non-zero exit code.", false);
        }
        return StepExecutionResult.success("Server checks completed.");
    }

    private boolean shouldCreateFinding(String cleanLine) {
        return cleanLine.startsWith("+")
                && !cleanLine.contains("Target IP:")
                && !cleanLine.contains("Target Hostname:")
                && !cleanLine.contains("Target Port:")
                && !cleanLine.contains("Platform:")
                && !cleanLine.contains("Start Time:")
                && !cleanLine.contains("End Time:")
                && !cleanLine.contains("host(s) tested")
                && !cleanLine.contains("requests:")
                && !cleanLine.contains("No CGI Directories found");
    }

    private String buildFindingTitle(String cleanLine) {
        String normalized = cleanLine.replaceFirst("^\\+\\s*", "");
        if (normalized.contains("Suggested security header missing:")) {
            return "Missing Security Header";
        }
        if (normalized.contains("Contains authorization information")) {
            return "Exposed Authorization File";
        }
        if (normalized.contains("This might be interesting")) {
            return "Interesting Resource Exposed";
        }
        if (normalized.contains("X-Content-Type-Options header is not set")) {
            return "Missing X-Content-Type-Options Header";
        }
        if (normalized.contains("contains 1 entry which should be manually viewed")) {
            return "Robots File Requires Review";
        }
        if (normalized.contains("Uncommon header")) {
            return "Uncommon Response Header Exposed";
        }
        return "Security Result";
    }

    private String extractAffectedUrl(String targetUrl, String cleanLine) {
        int pathStart = cleanLine.indexOf(" /");
        int separator = cleanLine.indexOf(':');
        if (pathStart >= 0 && separator > pathStart) {
            String path = cleanLine.substring(pathStart + 1, separator).trim();
            if (path.startsWith("/")) {
                return targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) + path : targetUrl + path;
            }
        }
        return targetUrl;
    }
}
