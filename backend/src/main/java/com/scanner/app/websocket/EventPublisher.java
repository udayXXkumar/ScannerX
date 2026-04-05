package com.scanner.app.websocket;

import com.scanner.app.domain.Finding;
import com.scanner.app.service.ScanActivityService;
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
            findingPayload.put("category", finding.getCategory());
            findingPayload.put("title", finding.getTitle());
            findingPayload.put("severity", finding.getSeverity());
            findingPayload.put("status", finding.getStatus());
            findingPayload.put("affectedUrl", finding.getAffectedUrl());
            findingPayload.put("description", finding.getDescription());
            findingPayload.put("aiDescription", finding.getAiDescription());
            findingPayload.put("exploitNarrative", finding.getExploitNarrative());
            findingPayload.put("aiEnrichmentStatus", finding.getAiEnrichmentStatus());
            findingPayload.put("aiModel", finding.getAiModel());
            findingPayload.put("aiEnrichedAt", finding.getAiEnrichedAt());
            findingPayload.put("aiEnrichmentError", finding.getAiEnrichmentError());
            findingPayload.put("createdAt", finding.getCreatedAt());
            if (finding.getTarget() != null) {
                Map<String, Object> targetPayload = new LinkedHashMap<>();
                targetPayload.put("id", finding.getTarget().getId());
                targetPayload.put("name", finding.getTarget().getName());
                targetPayload.put("baseUrl", finding.getTarget().getBaseUrl());
                targetPayload.put("domain", finding.getTarget().getDomain());
                findingPayload.put("target", targetPayload);
            }
            if (finding.getScan() != null) {
                Map<String, Object> scanPayload = new LinkedHashMap<>();
                scanPayload.put("id", finding.getScan().getId());
                scanPayload.put("name", finding.getScan().getName());
                findingPayload.put("scan", scanPayload);
            }
            return findingPayload;
        }
        return data;
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
