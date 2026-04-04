package com.scanner.app.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TierRuntimeAvailabilityService {

    public static final String USER_SAFE_TIER_UNAVAILABLE_MESSAGE =
            "This scan tier is not available right now, it is being updated Please try again later";

    private static final Logger log = LoggerFactory.getLogger(TierRuntimeAvailabilityService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final ToolExecutionService toolExecutionService;
    private final ZapDaemonManager zapDaemonManager;
    private final Map<ScanTier, CachedAvailability> availabilityCache = new ConcurrentHashMap<>();

    public TierRuntimeAvailabilityService(ToolExecutionService toolExecutionService, ZapDaemonManager zapDaemonManager) {
        this.toolExecutionService = toolExecutionService;
        this.zapDaemonManager = zapDaemonManager;
    }

    public void assertTierAvailable(ScanTier tier) {
        CachedAvailability cached = availabilityCache.get(tier);
        if (cached != null && cached.expiresAt().isAfter(LocalDateTime.now())) {
            if (cached.available()) {
                return;
            }
            throw new IllegalStateException(cached.reason());
        }

        try {
            validateDependencies(tier);
            availabilityCache.put(tier, new CachedAvailability(true, "available", LocalDateTime.now().plus(CACHE_TTL)));
        } catch (RuntimeException exception) {
            availabilityCache.put(tier, new CachedAvailability(false, exception.getMessage(), LocalDateTime.now().plus(CACHE_TTL)));
            throw exception;
        }
    }

    private void validateDependencies(ScanTier tier) {
        List<String> requiredExecutables = switch (tier) {
            case FAST -> List.of("whatweb", "nuclei", "zaproxy");
            case MEDIUM -> List.of("whatweb", "ffuf", "nuclei", "nikto", "zaproxy");
            case DEEP -> List.of("whatweb", "ffuf", "nuclei", "nikto", "dalfox", "sqlmap", "zaproxy");
        };

        List<String> missingExecutables = requiredExecutables.stream()
                .filter(executable -> !toolExecutionService.isExecutableAvailable(executable))
                .toList();

        if (!missingExecutables.isEmpty()) {
            throw new IllegalStateException("Missing required scanner dependencies: " + String.join(", ", missingExecutables));
        }

        ZapDaemonManager.ZapCapabilities capabilities = zapDaemonManager.probeCapabilities(Duration.ofSeconds(45));
        boolean valid = switch (tier) {
            case FAST -> capabilities.spiderAvailable() && capabilities.passiveScanAvailable();
            case MEDIUM, DEEP -> capabilities.spiderAvailable()
                    && capabilities.ajaxSpiderAvailable()
                    && capabilities.passiveScanAvailable()
                    && capabilities.activeScanAvailable();
        };

        if (!valid) {
            throw new IllegalStateException("Local ZAP daemon capabilities are incomplete for tier " + tier.name());
        }

        log.info("Tier {} runtime preflight passed", tier.name());
    }

    private record CachedAvailability(boolean available, String reason, LocalDateTime expiresAt) {
    }
}
