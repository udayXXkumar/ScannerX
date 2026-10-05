package com.scanner.app.websocket;

import com.scanner.app.domain.Finding;
import com.scanner.app.service.ScanActivityService;
import org.hibernate.LazyInitializationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ScanActivityService scanActivityService;

    public EventPublisher(SimpMessagingTemplate messagingTemplate, ScanActivityService scanActivityService) {
        this.messagingTemplate = messagingTemplate;
        this.scanActivityService = scanActivityService;
    }

    public void publishScanEvent(Long scanId, String eventType, Object data) {
        publishScanEvent(scanId, eventType, data, null);
    }

    public void publishScanEvent(Long scanId, String eventType, Object data, Integer stageOrder) {
        LocalDateTime timestamp = LocalDateTime.now();
        Map<String, Object> payload = buildPayload(scanId, eventType, data, stageOrder, timestamp);

        messagingTemplate.convertAndSend("/topic/scans/" + scanId, (Object) payload);

        if (!"FINDING_FOUND".equalsIgnoreCase(eventType)) {
            scanActivityService.record(scanId, eventType, stageOrder, buildActivityMessage(eventType, data), timestamp);
        }
    }

    public void publishScanProgress(Long scanId, Map<String, Object> data) {
        publishTransientScanEvent(scanId, "SCAN_PROGRESS", data, data == null ? null : (Integer) data.get("currentStageOrder"));
    }

    public void publishScanStatus(Long scanId, Map<String, Object> data) {
        publishTransientScanEvent(scanId, "SCAN_STATUS", data, data == null ? null : (Integer) data.get("currentStageOrder"));
    }

    private void publishTransientScanEvent(Long scanId, String eventType, Object data, Integer stageOrder) {
        LocalDateTime timestamp = LocalDateTime.now();
        messagingTemplate.convertAndSend("/topic/scans/" + scanId, (Object) buildPayload(scanId, eventType, data, stageOrder, timestamp));
    }

    private Map<String, Object> buildPayload(Long scanId, String eventType, Object data, Integer stageOrder, LocalDateTime timestamp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", eventType);
        payload.put("scanId", scanId);
        payload.put("stageOrder", stageOrder);
        payload.put("timestamp", timestamp);
        payload.put("data", serializeEventData(data));
        return payload;
    }

    private Object serializeEventData(Object data) {
        if (data instanceof Finding finding) {
            Map<String, Object> findingPayload = new LinkedHashMap<>();
            findingPayload.put("id", finding.getId());
            findingPayload.put("category", safeRead(() -> finding.getCategory(), null));
            findingPayload.put("title", safeRead(() -> finding.getTitle(), null));
            findingPayload.put("severity", safeRead(() -> finding.getSeverity(), null));
            findingPayload.put("status", safeRead(() -> finding.getStatus(), null));
            findingPayload.put("affectedUrl", safeRead(() -> finding.getAffectedUrl(), null));
            findingPayload.put("description", safeRead(() -> finding.getDescription(), null));
            findingPayload.put("aiDescription", safeRead(() -> finding.getAiDescription(), null));
            findingPayload.put("exploitNarrative", safeRead(() -> finding.getExploitNarrative(), null));
            findingPayload.put("aiEnrichmentStatus", safeRead(() -> finding.getAiEnrichmentStatus(), null));
            findingPayload.put("aiModel", safeRead(() -> finding.getAiModel(), null));
            findingPayload.put("aiEnrichedAt", safeRead(() -> finding.getAiEnrichedAt(), null));
            findingPayload.put("aiEnrichmentError", safeRead(() -> finding.getAiEnrichmentError(), null));
            findingPayload.put("createdAt", safeRead(() -> finding.getCreatedAt(), null));
            try {
                if (finding.getTarget() != null) {
                    Map<String, Object> targetPayload = new LinkedHashMap<>();
                    targetPayload.put("id", safeRead(() -> finding.getTarget().getId(), null));
                    targetPayload.put("name", safeRead(() -> finding.getTarget().getName(), null));
                    targetPayload.put("baseUrl", safeRead(() -> finding.getTarget().getBaseUrl(), null));
                    targetPayload.put("domain", safeRead(() -> finding.getTarget().getDomain(), null));
                    findingPayload.put("target", targetPayload);
                }
            } catch (RuntimeException exception) {
                if (!isLazyInitializationFailure(exception)) {
                    throw exception;
                }
            }
            try {
                if (finding.getScan() != null) {
                    Map<String, Object> scanPayload = new LinkedHashMap<>();
                    scanPayload.put("id", safeRead(() -> finding.getScan().getId(), null));
                    scanPayload.put("name", safeRead(() -> finding.getScan().getName(), null));
                    findingPayload.put("scan", scanPayload);
                }
            } catch (RuntimeException exception) {
                if (!isLazyInitializationFailure(exception)) {
                    throw exception;
                }
            }
            return findingPayload;
        }
        return data;
    }

    private boolean isLazyInitializationFailure(Throwable exception) {
        return exception instanceof LazyInitializationException
                || (exception.getCause() != null && isLazyInitializationFailure(exception.getCause()));
    }

    private <T> T safeRead(java.util.concurrent.Callable<T> reader, T fallback) {
        try {
            return reader.call();
        } catch (RuntimeException exception) {
            if (isLazyInitializationFailure(exception)) {
                return fallback;
            }
            throw exception;
        } catch (Exception exception) {
            if (isLazyInitializationFailure(exception)) {
                return fallback;
            }
            throw new IllegalStateException("Unable to read event payload value", exception);
        }
    }

    private String buildActivityMessage(String eventType, Object data) {
        if (data instanceof Map<?, ?> map && map.containsKey("message")) {
            return String.valueOf(map.get("message"));
        }

        if (data != null && !(data instanceof Finding)) {
            return String.valueOf(data);
        }

        return switch (String.valueOf(eventType).toUpperCase()) {
            case "SCAN_STARTED" -> "Scan started.";
            case "SCAN_RESUMED" -> "Scan resumed.";
            case "SCAN_COMPLETED" -> "Scan completed.";
            case "SCAN_FAILED" -> "Scan failed.";
            case "SCAN_CANCELLED" -> "Scan cancelled.";
            case "PAUSE_REQUESTED" -> "Pause requested.";
            case "SCAN_PAUSED" -> "Scan paused.";
            case "STAGE_STARTED" -> "Stage started.";
            case "STAGE_COMPLETED" -> "Stage completed.";
            case "STAGE_FAILED" -> "Stage could not be completed.";
            case "FINDING_ENRICHED" -> "AI enrichment completed for a finding.";
            default -> null;
        };
    }
}
