package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

@Component
public class ZapApiClient {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final int ALERT_PAGE_SIZE = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZapApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean waitUntilReady(ZapDaemonManager.ZapDaemonSession session, Duration timeout) {
        return waitForCondition(timeout, () -> {
            try {
                JsonNode node = call(session, "core", "view", "version", Map.of());
                return node.path("version").asText("").trim().length() > 0;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    public boolean supportsSpider(ZapDaemonManager.ZapDaemonSession session) {
        return componentAvailable(session, "spider", "view", "scans", Map.of());
    }

    public boolean supportsAjaxSpider(ZapDaemonManager.ZapDaemonSession session) {
        return componentAvailable(session, "ajaxSpider", "view", "status", Map.of());
    }

    public boolean supportsPassiveScan(ZapDaemonManager.ZapDaemonSession session) {
        return componentAvailable(session, "pscan", "view", "recordsToScan", Map.of());
    }

    public boolean supportsActiveScan(ZapDaemonManager.ZapDaemonSession session) {
        return componentAvailable(session, "ascan", "view", "scans", Map.of());
    }

    public void accessUrl(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        call(session, "core", "action", "accessUrl", Map.of(
                "url", targetUrl,
                "followRedirects", "true"
        ));
    }

    public String startSpider(ZapDaemonManager.ZapDaemonSession session, String targetUrl, int maxChildren) throws Exception {
        JsonNode node = call(session, "spider", "action", "scan", Map.of(
                "url", targetUrl,
                "maxChildren", String.valueOf(Math.max(maxChildren, 1)),
                "recurse", "true",
                "subtreeOnly", "false"
        ));
        return readActionId(node, "scan");
    }

    public void waitForSpider(ZapDaemonManager.ZapDaemonSession session, String scanId, Duration timeout) throws Exception {
        waitForSpider(session, scanId, timeout, null);
    }

    public void waitForSpider(ZapDaemonManager.ZapDaemonSession session, String scanId, Duration timeout, IntConsumer progressConsumer) throws Exception {
        waitForIntegerProgress(
                timeout,
                () -> {
                    JsonNode statusNode = call(session, "spider", "view", "status", Map.of("scanId", scanId));
                    return parseInteger(statusNode.path("status").asText("0"));
                },
                progressConsumer,
                progress -> progress >= 100,
                "Spider did not finish in time."
        );
    }

    public void startAjaxSpider(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        call(session, "ajaxSpider", "action", "scan", Map.of(
                "url", targetUrl,
                "inScope", "false"
        ));
    }

    public void waitForAjaxSpider(ZapDaemonManager.ZapDaemonSession session, Duration timeout) throws Exception {
        waitForCompletion(timeout, () -> {
            JsonNode statusNode = call(session, "ajaxSpider", "view", "status", Map.of());
            return "stopped".equalsIgnoreCase(statusNode.path("status").asText(""));
        }, "JavaScript crawl did not finish in time.");
    }

    public void waitForPassiveDrain(ZapDaemonManager.ZapDaemonSession session, Duration timeout) throws Exception {
        waitForPassiveDrain(session, timeout, null);
    }

    public void waitForPassiveDrain(ZapDaemonManager.ZapDaemonSession session, Duration timeout, IntConsumer progressConsumer) throws Exception {
        final int[] initialBacklog = {-1};
        waitForIntegerProgress(
                timeout,
                () -> {
                    JsonNode node = call(session, "pscan", "view", "recordsToScan", Map.of());
                    int remaining = parseInteger(node.path("recordsToScan").asText("0"));
                    if (initialBacklog[0] < 0) {
                        initialBacklog[0] = Math.max(remaining, 0);
                    }
                    if (progressConsumer != null) {
                        int total = Math.max(initialBacklog[0], 1);
                        int progressed = Math.max(0, total - remaining);
                        progressConsumer.accept(Math.min(100, (int) Math.round((progressed * 100.0) / total)));
                    }
                    return remaining;
                },
                null,
                remaining -> remaining <= 0,
                "Passive analysis did not finish in time."
        );
    }

    public String startActiveScan(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        JsonNode node = call(session, "ascan", "action", "scan", Map.of(
                "url", targetUrl,
                "recurse", "true",
                "inScopeOnly", "false"
        ));
        return readActionId(node, "scan");
    }

    public void waitForActiveScan(ZapDaemonManager.ZapDaemonSession session, String scanId, Duration timeout) throws Exception {
        waitForActiveScan(session, scanId, timeout, null);
    }

    public void waitForActiveScan(ZapDaemonManager.ZapDaemonSession session, String scanId, Duration timeout, IntConsumer progressConsumer) throws Exception {
        waitForIntegerProgress(
                timeout,
                () -> {
                    JsonNode statusNode = call(session, "ascan", "view", "status", Map.of("scanId", scanId));
                    return parseInteger(statusNode.path("status").asText("0"));
                },
                progressConsumer,
                progress -> progress >= 100,
                "Controlled active verification did not finish in time."
        );
    }

    public List<String> fetchUrls(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        JsonNode node = call(session, "core", "view", "urls", Map.of("baseurl", targetUrl));
        List<String> urls = new ArrayList<>();
        JsonNode values = node.path("urls");
        if (values.isArray()) {
            values.forEach(value -> {
                String url = value.asText("").trim();
                if (!url.isBlank()) {
                    urls.add(url);
                }
            });
        }
        return urls;
    }

    public List<ZapAlert> fetchAlerts(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        List<ZapAlert> alerts = new ArrayList<>();
        int start = 0;

        while (true) {
            JsonNode node = call(session, "alert", "view", "alerts", Map.of(
                    "baseurl", targetUrl,
                    "start", String.valueOf(start),
                    "count", String.valueOf(ALERT_PAGE_SIZE)
            ));

            JsonNode values = node.path("alerts");
            if (!values.isArray() || values.isEmpty()) {
                break;
            }

            values.forEach(alert -> alerts.add(new ZapAlert(
                    alert.path("name").asText(alert.path("alert").asText("Security Result")),
                    alert.path("risk").asText(alert.path("riskdesc").asText("Informational")),
                    alert.path("riskdesc").asText(alert.path("risk").asText("Informational")),
                    alert.path("confidence").asText(alert.path("confidencedesc").asText("Medium")),
                    alert.path("confidencedesc").asText(alert.path("confidence").asText("Medium")),
                    alert.path("description").asText(alert.path("desc").asText("")),
                    alert.path("solution").asText(""),
                    alert.path("reference").asText(""),
                    alert.path("param").asText(""),
                    alert.path("evidence").asText(""),
                    alert.path("other").asText(""),
                    alert.path("url").asText(targetUrl),
                    alert.toString()
            )));

            if (values.size() < ALERT_PAGE_SIZE) {
                break;
            }
            start += ALERT_PAGE_SIZE;
        }

        return alerts;
    }

    public void shutdown(ZapDaemonManager.ZapDaemonSession session) {
        try {
            call(session, "core", "action", "shutdown", Map.of());
        } catch (Exception ignored) {
            // Best-effort shutdown only.
        }
    }

    private boolean componentAvailable(
            ZapDaemonManager.ZapDaemonSession session,
            String component,
            String type,
            String name,
            Map<String, String> params
    ) {
        try {
            call(session, component, type, name, params);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void waitForCompletion(Duration timeout, CheckedBooleanSupplier completionCheck, String timeoutMessage) throws Exception {
        if (!waitForCondition(timeout, () -> {
            try {
                return completionCheck.getAsBoolean();
            } catch (Exception ignored) {
                return false;
            }
        })) {
            throw new IllegalStateException(timeoutMessage);
        }
    }

    private void waitForIntegerProgress(
            Duration timeout,
            CheckedIntegerSupplier progressSupplier,
            IntConsumer progressConsumer,
            java.util.function.IntPredicate completedPredicate,
            String timeoutMessage
    ) throws Exception {
        long deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
        int lastProgress = Integer.MIN_VALUE;

        while (System.nanoTime() <= deadline) {
            int progress;
            try {
                progress = progressSupplier.getAsInt();
            } catch (Exception ignored) {
                progress = lastProgress == Integer.MIN_VALUE ? 0 : lastProgress;
            }

            if (progressConsumer != null && progress > lastProgress) {
                progressConsumer.accept(progress);
            }

            if (completedPredicate.test(progress)) {
                return;
            }

            lastProgress = Math.max(lastProgress, progress);
            try {
                TimeUnit.MILLISECONDS.sleep(DEFAULT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(timeoutMessage, interruptedException);
            }
        }

        throw new IllegalStateException(timeoutMessage);
    }

    private boolean waitForCondition(Duration timeout, BooleanSupplier condition) {
        long deadline = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() <= deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(DEFAULT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private JsonNode call(
            ZapDaemonManager.ZapDaemonSession session,
            String component,
            String type,
            String name,
            Map<String, String> params
    ) throws Exception {
        Map<String, String> query = new LinkedHashMap<>(params);
        query.put("apikey", session.apiKey());

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(buildUri(session.baseUrl(), component, type, name, query))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("ZAP API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode code = root.path("code");
        if (code.isTextual() && !code.asText("").isBlank()) {
            String normalizedCode = code.asText("").toLowerCase(Locale.ROOT);
            if (!"does_not_exist".equals(normalizedCode)) {
                throw new IllegalStateException(root.path("message").asText("ZAP API error."));
            }
        }
        return root;
    }

    private URI buildUri(String baseUrl, String component, String type, String name, Map<String, String> params) {
        StringBuilder builder = new StringBuilder(baseUrl)
                .append("/JSON/")
                .append(component)
                .append("/")
                .append(type)
                .append("/")
                .append(name)
                .append("/?");

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(builder.toString());
    }

    private String readActionId(JsonNode node, String field) {
        JsonNode direct = node.path(field);
        if (direct.isTextual() && !direct.asText("").isBlank()) {
            return direct.asText("");
        }
        JsonNode result = node.path("Result");
        if (result.isTextual() && !result.asText("").isBlank()) {
            return result.asText("");
        }
        return "0";
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedIntegerSupplier {
        int getAsInt() throws Exception;
    }

    public record ZapAlert(
            String name,
            String risk,
            String riskDescription,
            String confidence,
            String confidenceDescription,
            String description,
            String solution,
            String reference,
            String parameter,
            String evidence,
            String otherInfo,
            String url,
            String rawJson
    ) {
    }
}
