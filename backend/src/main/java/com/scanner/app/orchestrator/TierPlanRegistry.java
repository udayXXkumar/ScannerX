package com.scanner.app.orchestrator;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class TierPlanRegistry {

    public TierPlan planFor(ScanTier tier, boolean timeoutsEnabled) {
        return switch (tier) {
            case FAST -> buildFastPlan(timeoutsEnabled);
            case MEDIUM -> buildMediumPlan(timeoutsEnabled);
            case DEEP -> buildDeepPlan(timeoutsEnabled);
        };
    }

    private TierPlan buildFastPlan(boolean timeoutsEnabled) {
        return new TierPlan(
                ScanTier.FAST,
                timeoutsEnabled ? Duration.ofSeconds(300) : null,
                List.of(
                        stage(1, "Preparation", step("normalize-url", "Normalize URL", 10, null, false)),
                        stage(2, "Baseline", step("httpx", "Reachability", 10, timeout(timeoutsEnabled, 20), false),
                                step("whatweb", "Fingerprinting", 20, timeout(timeoutsEnabled, 30), false)),
                        stage(3, "Passive Checks", step("zap-baseline", "Passive Baseline", 10, timeout(timeoutsEnabled, 60), false,
                                        Map.of("maxChildren", "25")),
                                step("nuclei-root", "Root Checks", 20, timeout(timeoutsEnabled, 60), true)),
                        stage(4, "Light Discovery", step("light-crawler", "Light Discovery", 10, timeout(timeoutsEnabled, 20), true,
                                Map.of("depth", "2", "maxUrls", "30"))),
                        stage(5, "Re-scan Discovered Endpoints", step("nuclei-discovered", "Endpoint Checks", 10, timeout(timeoutsEnabled, 60), false,
                                Map.of(
                                        "targetSource", "discovered",
                                        "maxTargets", "8",
                                        "batchSize", "4",
                                        "batchTimeoutSeconds", "18"
                                ))),
                        stage(6, "Final Correlation", step("finalize-report", "Build Report", 10, timeout(timeoutsEnabled, 20), false))
                )
        );
    }

    private TierPlan buildMediumPlan(boolean timeoutsEnabled) {
        return new TierPlan(
                ScanTier.MEDIUM,
                timeoutsEnabled ? Duration.ofSeconds(2700) : null,
                List.of(
                        stage(1, "Baseline", step("httpx", "Reachability", 10, timeout(timeoutsEnabled, 20), false),
                                step("whatweb", "Fingerprinting", 20, timeout(timeoutsEnabled, 30), false)),
                        stage(2, "Bounded Discovery Expansion", step("ffuf-medium", "Bounded Discovery", 10, timeout(timeoutsEnabled, 120), true,
                                Map.of("mode", "medium", "rate", "50", "threads", "15", "recursion", "false"))),
                        stage(3, "Crawl", step("zap-spider", "Crawl", 10, timeout(timeoutsEnabled, 150), true,
                                        Map.of("maxChildren", "90")),
                                step("zap-ajax", "JavaScript Crawl", 20, timeout(timeoutsEnabled, 180), true)),
                        stage(4, "Asset Classification", step("classify-assets", "Classify Assets", 10, timeout(timeoutsEnabled, 45), true)),
                        stage(5, "Passive and Server Checks", step("nuclei-discovered", "Endpoint Checks", 10, timeout(timeoutsEnabled, 180), false,
                                Map.of(
                                        "targetSource", "discovered",
                                        "maxTargets", "24",
                                        "batchSize", "6",
                                        "batchTimeoutSeconds", "45"
                                )),
                                step("nikto", "Server Checks", 20, timeout(timeoutsEnabled, 240), true),
                                step("zap-passive", "Passive Verification", 30, timeout(timeoutsEnabled, 120), true)),
                        stage(6, "Controlled Active Verification", step("zap-active", "Controlled Active Validation", 10, timeout(timeoutsEnabled, 420), false)),
                        stage(7, "Final Correlation", step("finalize-report", "Build Report", 10, timeout(timeoutsEnabled, 60), false))
                )
        );
    }

    private TierPlan buildDeepPlan(boolean timeoutsEnabled) {
        return new TierPlan(
                ScanTier.DEEP,
                timeoutsEnabled ? Duration.ofSeconds(5400) : null,
                List.of(
                        stage(1, "Baseline", step("httpx", "Reachability", 10, timeout(timeoutsEnabled, 20), false),
                                step("whatweb", "Fingerprinting", 20, timeout(timeoutsEnabled, 30), false)),
                        stage(2, "Deep Discovery", step("ffuf-deep", "Recursive Discovery", 10, timeout(timeoutsEnabled, 150), true,
                                Map.of("mode", "deep", "rate", "35", "threads", "20", "recursion", "true", "recursionDepth", "1")),
                                step("expand-scope", "Expand Scope", 20, timeout(timeoutsEnabled, 60), true)),
                        stage(3, "Expanded Crawl", step("zap-spider", "Crawl", 10, timeout(timeoutsEnabled, 240), true,
                                        Map.of("maxChildren", "140")),
                                step("zap-ajax", "JavaScript Crawl", 20, timeout(timeoutsEnabled, 240), true)),
                        stage(4, "Full Passive Checks", step("nuclei-discovered", "Endpoint Checks", 10, timeout(timeoutsEnabled, 240), false,
                                Map.of(
                                        "targetSource", "discovered",
                                        "maxTargets", "40",
                                        "batchSize", "8",
                                        "batchTimeoutSeconds", "60"
                                )),
                                step("nikto", "Server Checks", 20, timeout(timeoutsEnabled, 300), true)),
                        stage(5, "Controlled Attack Validation", step("zap-active", "Controlled Active Validation", 10, timeout(timeoutsEnabled, 600), false),
                                step("dalfox", "Reflected Input Validation", 20, timeout(timeoutsEnabled, 120), true),
                                step("sqlmap", "Injection Validation", 30, timeout(timeoutsEnabled, 300), true,
                                        Map.of("batchCount", "5", "perBatchSeconds", "60"))),
                        stage(6, "Final Correlation", step("finalize-report", "Build Report", 10, timeout(timeoutsEnabled, 60), false))
                )
        );
    }

    private PlanStage stage(int order, String label, PlanStep... steps) {
        return new PlanStage(order, label, List.of(steps));
    }

    private PlanStep step(String key, String label, int order, Duration timeout, boolean retryable) {
        return step(key, label, order, timeout, retryable, Map.of());
    }

    private PlanStep step(String key, String label, int order, Duration timeout, boolean retryable, Map<String, String> settings) {
        return new PlanStep(key, label, order, timeout, retryable, settings);
    }

    private Duration timeout(boolean timeoutsEnabled, long seconds) {
        return timeoutsEnabled ? Duration.ofSeconds(seconds) : null;
    }
}
