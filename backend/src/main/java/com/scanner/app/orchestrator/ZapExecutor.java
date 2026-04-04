package com.scanner.app.orchestrator;

import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ZapExecutor extends AbstractFindingExecutor {

    private final ZapDaemonManager zapDaemonManager;
    private final ZapApiClient zapApiClient;

    public ZapExecutor(
            FindingService findingService,
            ToolExecutionService toolExecutionService,
            ZapDaemonManager zapDaemonManager,
            ZapApiClient zapApiClient
    ) {
        super(findingService, toolExecutionService);
        this.zapDaemonManager = zapDaemonManager;
        this.zapApiClient = zapApiClient;
    }

    @Override
    public String getExecutorName() {
        return "zap";
    }

    @Override
    public boolean supports(PlanStep step) {
        return step.key().startsWith("zap-");
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        String targetUrl = context.getNormalizedTargetUrl();
        ZapDaemonManager.ZapDaemonSession session = zapDaemonManager.ensureSession(scan.getId(), context);
        OperationBudget operationBudget = new OperationBudget(step.timeout());

        try {
            return switch (step.key()) {
                case "zap-baseline" -> runBaseline(scan, step, context, eventPublisher, session, targetUrl, operationBudget);
                case "zap-spider" -> runSpider(scan, step, context, session, targetUrl, operationBudget);
                case "zap-ajax" -> runAjaxSpider(scan, step, context, session, targetUrl, operationBudget);
                case "zap-passive" -> runPassiveVerification(scan, context, eventPublisher, session, targetUrl, operationBudget);
                case "zap-active" -> runActiveVerification(scan, context, eventPublisher, session, targetUrl, operationBudget);
                default -> StepExecutionResult.skipped("Verification scope was skipped.");
            };
        } catch (IllegalStateException exception) {
            boolean timedOut = exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("time");
            return StepExecutionResult.nonFatalFailure(
                    step.key().equals("zap-active")
                            ? "Controlled active verification could not be completed in this environment."
                            : step.label() + " could not be completed in this environment.",
                    timedOut
            );
        }
    }

    private StepExecutionResult runBaseline(
            Scan scan,
            PlanStep step,
            ScanExecutionContext context,
            EventPublisher eventPublisher,
            ZapDaemonManager.ZapDaemonSession session,
            String targetUrl,
            OperationBudget operationBudget
    ) throws Exception {
        accessSeedUrls(session, seedUrls(context, targetUrl, 8));
        String spiderScanId = zapApiClient.startSpider(session, targetUrl, 25);
        zapApiClient.waitForSpider(session, spiderScanId, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));
        zapApiClient.waitForPassiveDrain(session, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));

        ScopeData scopeData = collectScope(session, targetUrl);
        persistAlerts(scan, eventPublisher, context, targetUrl, zapApiClient.fetchAlerts(session, targetUrl), "Passive");
        return StepExecutionResult.success("Passive baseline completed.", scopeData.urls(), scopeData.forms(), scopeData.queryParameters(), scopeData.jsonEndpoints());
    }

    private StepExecutionResult runSpider(
            Scan scan,
            PlanStep step,
            ScanExecutionContext context,
            ZapDaemonManager.ZapDaemonSession session,
            String targetUrl,
            OperationBudget operationBudget
    ) throws Exception {
        accessSeedUrls(session, seedUrls(context, targetUrl, "DEEP".equalsIgnoreCase(scan.getTier()) ? 25 : 15));
        String spiderScanId = zapApiClient.startSpider(session, targetUrl, step.intSetting("maxChildren", 80));
        zapApiClient.waitForSpider(session, spiderScanId, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));

        ScopeData scopeData = collectScope(session, targetUrl);
        return StepExecutionResult.success("Crawl completed.", scopeData.urls(), scopeData.forms(), scopeData.queryParameters(), scopeData.jsonEndpoints());
    }

    private StepExecutionResult runAjaxSpider(
            Scan scan,
            PlanStep step,
            ScanExecutionContext context,
            ZapDaemonManager.ZapDaemonSession session,
            String targetUrl,
            OperationBudget operationBudget
    ) throws Exception {
        accessSeedUrls(session, seedUrls(context, targetUrl, "DEEP".equalsIgnoreCase(scan.getTier()) ? 25 : 15));
        zapApiClient.startAjaxSpider(session, targetUrl);
        zapApiClient.waitForAjaxSpider(session, operationBudget.remaining());
        context.markForwardProgress();
        zapApiClient.waitForPassiveDrain(session, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));

        ScopeData scopeData = collectScope(session, targetUrl);
        return StepExecutionResult.success("JavaScript crawl completed.", scopeData.urls(), scopeData.forms(), scopeData.queryParameters(), scopeData.jsonEndpoints());
    }

    private StepExecutionResult runPassiveVerification(
            Scan scan,
            ScanExecutionContext context,
            EventPublisher eventPublisher,
            ZapDaemonManager.ZapDaemonSession session,
            String targetUrl,
            OperationBudget operationBudget
    ) throws Exception {
        accessSeedUrls(session, seedUrls(context, targetUrl, 25));
        zapApiClient.waitForPassiveDrain(session, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));

        ScopeData scopeData = collectScope(session, targetUrl);
        persistAlerts(scan, eventPublisher, context, targetUrl, zapApiClient.fetchAlerts(session, targetUrl), "Passive");
        return StepExecutionResult.success("Passive verification completed.", scopeData.urls(), scopeData.forms(), scopeData.queryParameters(), scopeData.jsonEndpoints());
    }

    private StepExecutionResult runActiveVerification(
            Scan scan,
            ScanExecutionContext context,
            EventPublisher eventPublisher,
            ZapDaemonManager.ZapDaemonSession session,
            String targetUrl,
            OperationBudget operationBudget
    ) throws Exception {
        accessSeedUrls(session, seedUrls(context, targetUrl, 30));
        String scanId = zapApiClient.startActiveScan(session, targetUrl);
        zapApiClient.waitForActiveScan(session, scanId, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));
        zapApiClient.waitForPassiveDrain(session, operationBudget.remaining(), progress -> context.updateStageProgressPercent(progress, true));

        ScopeData scopeData = collectScope(session, targetUrl);
        persistAlerts(scan, eventPublisher, context, targetUrl, zapApiClient.fetchAlerts(session, targetUrl), "Active");
        return StepExecutionResult.success("Controlled active verification completed.", scopeData.urls(), scopeData.forms(), scopeData.queryParameters(), scopeData.jsonEndpoints());
    }

    private void persistAlerts(
            Scan scan,
            EventPublisher eventPublisher,
            ScanExecutionContext context,
            String targetUrl,
            List<ZapApiClient.ZapAlert> alerts,
            String category
    ) {
        for (ZapApiClient.ZapAlert alert : alerts) {
            saveFinding(
                    scan,
                    eventPublisher,
                    context,
                    "ZAP",
                    category,
                    firstNonBlank(alert.name(), "Security Result"),
                    normalizeZapSeverity(alert.risk(), alert.riskDescription(), alert.confidence(), alert.confidenceDescription()),
                    firstNonBlank(alert.url(), targetUrl),
                    buildAlertDescription(alert),
                    alert.rawJson()
            );
        }
    }

    private ScopeData collectScope(ZapDaemonManager.ZapDaemonSession session, String targetUrl) throws Exception {
        Set<String> discoveredUrls = new LinkedHashSet<>(zapApiClient.fetchUrls(session, targetUrl));
        if (discoveredUrls.isEmpty()) {
            discoveredUrls.add(targetUrl);
        }

        Set<String> forms = new LinkedHashSet<>();
        Set<String> queryParameters = new LinkedHashSet<>();
        Set<String> jsonEndpoints = new LinkedHashSet<>();

        for (String url : discoveredUrls) {
            String normalizedUrl = String.valueOf(url);
            String lowerCaseUrl = normalizedUrl.toLowerCase(Locale.ROOT);
            if (looksLikeFormEndpoint(lowerCaseUrl)) {
                forms.add(normalizedUrl);
            }
            if (lowerCaseUrl.endsWith(".json") || lowerCaseUrl.contains("/api/")) {
                jsonEndpoints.add(normalizedUrl);
            }
            queryParameters.addAll(extractQueryParameters(normalizedUrl));
        }

        return new ScopeData(discoveredUrls, forms, queryParameters, jsonEndpoints);
    }

    private void accessSeedUrls(ZapDaemonManager.ZapDaemonSession session, Set<String> seedUrls) {
        for (String seedUrl : seedUrls) {
            try {
                zapApiClient.accessUrl(session, seedUrl);
            } catch (Exception ignored) {
                // Best effort scope warm-up only.
            }
        }
    }

    private Set<String> seedUrls(ScanExecutionContext context, String targetUrl, int limit) {
        LinkedHashSet<String> seedUrls = new LinkedHashSet<>();
        seedUrls.add(targetUrl);
        context.getDiscoveredUrls().stream()
                .filter(url -> url != null && !url.isBlank())
                .limit(Math.max(limit, 1))
                .forEach(seedUrls::add);
        return seedUrls;
    }

    private Set<String> extractQueryParameters(String url) {
        Set<String> parameters = new LinkedHashSet<>();
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart >= url.length() - 1) {
            return parameters;
        }

        String query = url.substring(queryStart + 1);
        for (String segment : query.split("&")) {
            int separator = segment.indexOf('=');
            parameters.add(separator >= 0 ? segment.substring(0, separator) : segment);
        }
        return parameters;
    }

    private boolean looksLikeFormEndpoint(String url) {
        return url.contains("login")
                || url.contains("register")
                || url.contains("submit")
                || url.contains("form");
    }

    private String buildAlertDescription(ZapApiClient.ZapAlert alert) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, alert.description());
        appendIfPresent(builder, alert.solution());
        appendIfPresent(builder, alert.reference());
        appendIfPresent(builder, alert.parameter() == null || alert.parameter().isBlank() ? null : "Parameter: " + alert.parameter());
        appendIfPresent(builder, alert.evidence() == null || alert.evidence().isBlank() ? null : "Evidence: " + alert.evidence());
        appendIfPresent(builder, alert.otherInfo());
        return builder.length() == 0 ? "Detected a security issue." : builder.toString().trim();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" ");
        }
        builder.append(value.trim());
    }

    private String normalizeZapSeverity(String risk, String riskDescription, String confidence, String confidenceDescription) {
        String combined = String.join(" ",
                firstNonBlank(risk, ""),
                firstNonBlank(riskDescription, ""),
                firstNonBlank(confidence, ""),
                firstNonBlank(confidenceDescription, "")
        ).toUpperCase(Locale.ROOT);

        if (combined.contains("CRITICAL")) return "CRITICAL";
        if (combined.contains("HIGH")) return "HIGH";
        if (combined.contains("MEDIUM")) return "MEDIUM";
        if (combined.contains("LOW")) return "LOW";
        return "INFO";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record ScopeData(
            Set<String> urls,
            Set<String> forms,
            Set<String> queryParameters,
            Set<String> jsonEndpoints
    ) {
    }

    private static final class OperationBudget {
        private final long deadlineNanos;

        private OperationBudget(Duration timeout) {
            this.deadlineNanos = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();
        }

        private Duration remaining() {
            if (deadlineNanos == Long.MAX_VALUE) {
                return null;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofNanos(remainingNanos);
        }
    }
}
