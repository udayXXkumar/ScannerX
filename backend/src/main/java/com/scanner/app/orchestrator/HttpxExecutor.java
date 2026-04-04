package com.scanner.app.orchestrator;

import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class HttpxExecutor extends AbstractFindingExecutor {

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    public HttpxExecutor(FindingService findingService, ToolExecutionService toolExecutionService) {
        super(findingService, toolExecutionService);
    }

    @Override
    public String getExecutorName() {
        return "httpx";
    }

    @Override
    public boolean supports(PlanStep step) {
        return "httpx".equals(step.key());
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        String targetUrl = context.getNormalizedTargetUrl();
        BaselineProbeResult probe = runBaselineProbe(targetUrl, step.timeout());

        String description = "Baseline response collected."
                + (probe.statusCode().isBlank() ? "" : " Status: " + probe.statusCode() + ".")
                + (probe.title().isBlank() ? "" : " Title: " + probe.title() + ".")
                + (probe.server().isBlank() ? "" : " Server: " + probe.server() + ".")
                + (probe.technologies().isBlank() ? "" : " Technologies: " + probe.technologies() + ".");

        saveFinding(
                scan,
                eventPublisher,
                context,
                "httpx",
                "Baseline",
                "Baseline Response Profile",
                "INFO",
                targetUrl,
                description.trim(),
                probe.evidence()
        );

        return StepExecutionResult.success(
                "Baseline metadata collected.",
                Set.of(targetUrl),
                Set.of(),
                extractQueryParameters(targetUrl),
                Set.of()
        );
    }

    private BaselineProbeResult runBaselineProbe(String targetUrl, Duration timeout) throws Exception {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        HttpURLConnection connection = (HttpURLConnection) new URL(URI.create(targetUrl).toString()).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout((int) effectiveTimeout.toMillis());
        connection.setReadTimeout((int) effectiveTimeout.toMillis());
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ScannerX-Baseline/1.0");
        connection.connect();

        int statusCode = connection.getResponseCode();
        String responseBody = readResponseBody(connection);
        String server = valueOrEmpty(connection.getHeaderField("server"));
        String title = extractTitle(responseBody);
        String technologies = Stream.of("x-powered-by", "x-generator", "x-runtime", "x-aspnet-version", "content-type")
                .map(header -> {
                    String value = connection.getHeaderField(header);
                    return value == null || value.isBlank() ? "" : header + "=" + value;
                })
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));

        String evidence = buildEvidence(statusCode, server, title, technologies);
        return new BaselineProbeResult(String.valueOf(statusCode), title, server, technologies, evidence);
    }

    private String readResponseBody(HttpURLConnection connection) throws Exception {
        InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }

        try (InputStream closableStream = stream) {
            return new String(closableStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractTitle(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        Matcher matcher = TITLE_PATTERN.matcher(body);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replaceAll("\\s+", " ").trim();
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
                parameters.add((separator >= 0 ? segment.substring(0, separator) : segment).trim());
            }
        } catch (Exception ignored) {
            return parameters;
        }

        return parameters;
    }

    private String buildEvidence(int statusCode, String server, String title, String technologies) {
        return Stream.of(
                        "status=" + statusCode,
                        server.isBlank() ? "" : "server=" + server,
                        title.isBlank() ? "" : "title=" + title,
                        technologies.isBlank() ? "" : "metadata=" + technologies
                )
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private record BaselineProbeResult(
            String statusCode,
            String title,
            String server,
            String technologies,
            String evidence
    ) {
    }
}
