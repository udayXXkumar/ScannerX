package com.scanner.app.rest;

import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.orchestrator.ScanTier;
import com.scanner.app.orchestrator.TierRuntimeAvailabilityService;
import com.scanner.app.queue.ScanProducer;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanActivityRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.NotificationRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.ScanActivityService;
import com.scanner.app.websocket.EventPublisher;
import com.scanner.app.orchestrator.ToolExecutionService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/scans")
public class ScanController {
    private static final List<String> ACTIVE_SCAN_STATUSES = List.of("QUEUED", "RUNNING", "PAUSING");

    private final ScanRepository scanRepository;
    private final ScanProducer scanProducer;
    private final FindingRepository findingRepository;
    private final TargetRepository targetRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ScanActivityService scanActivityService;
    private final EventPublisher eventPublisher;
    private final ToolExecutionService toolExecutionService;
    private final TierRuntimeAvailabilityService tierRuntimeAvailabilityService;

    public ScanController(
            ScanRepository scanRepository,
            ScanProducer scanProducer,
            FindingRepository findingRepository,
            TargetRepository targetRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ScanActivityService scanActivityService,
            EventPublisher eventPublisher,
            ToolExecutionService toolExecutionService,
            TierRuntimeAvailabilityService tierRuntimeAvailabilityService
    ) {
        this.scanRepository = scanRepository;
        this.scanProducer = scanProducer;
        this.findingRepository = findingRepository;
        this.targetRepository = targetRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.scanActivityService = scanActivityService;
        this.eventPublisher = eventPublisher;
        this.toolExecutionService = toolExecutionService;
        this.tierRuntimeAvailabilityService = tierRuntimeAvailabilityService;
    }

    @GetMapping
    public ResponseEntity<List<Scan>> getAllScans(Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(scanRepository.findWithContextByUserIdOrderByCreatedAtDesc(currentUser.get().getId()));
    }

    @PostMapping
    public ResponseEntity<?> createScan(@RequestBody Scan scan, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        if (scanRepository.existsByUserIdAndStatusIn(currentUser.get().getId(), ACTIVE_SCAN_STATUSES)) {
            return ResponseEntity.status(409).body(Map.of(
                    "message",
                    "A scan is already running. Let it complete or cancel it before starting another."
            ));
        }

        Long targetId = scan.getTarget() != null ? scan.getTarget().getId() : null;
        if (targetId == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Target> targetOptional = targetRepository.findById(targetId);
        if (targetOptional.isEmpty() || !belongsToUser(targetOptional.get(), currentUser.get())) {
            return ResponseEntity.notFound().build();
        }

        scan.setUser(currentUser.get());
        scan.setTarget(targetOptional.get());
        scan.setName(resolveTargetScanName(targetOptional.get()));
        String tier = ScanTier.fromTargetValue(targetOptional.get().getDefaultTier()).toPersistedValue();
        try {
            tierRuntimeAvailabilityService.assertTierAvailable(ScanTier.fromTargetValue(tier));
        } catch (Exception exception) {
            return ResponseEntity.status(409).body(Map.of("message", TierRuntimeAvailabilityService.USER_SAFE_TIER_UNAVAILABLE_MESSAGE));
        }
        scan.setTier(tier);
        scan.setTimeoutsEnabled(targetOptional.get().getTimeoutsEnabled() == null ? Boolean.TRUE : targetOptional.get().getTimeoutsEnabled());
        scan.setProfileType(toLegacyProfile(tier));
        scan.setStatus("QUEUED");
        scan.setProgress(0);
        scan.setPauseRequested(Boolean.FALSE);
        scan.setCurrentStageOrder(null);
        scan.setResumeStageOrder(1);
        scan.setExecutionContextJson(null);
        scan.setNormalizedReportJson(null);
        scan.setCreatedAt(LocalDateTime.now());
        scan.setUpdatedAt(LocalDateTime.now());

        targetOptional.get().setLastScanAt(LocalDateTime.now());
        targetOptional.get().setUpdatedAt(LocalDateTime.now());
        targetRepository.save(targetOptional.get());

        Scan savedScan = scanRepository.save(scan);
        scanProducer.sendScanJob(savedScan.getId());
        return buildScanResponse(savedScan.getId(), currentUser.get().getId());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Scan> getScanById(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return scanRepository.findWithContextByIdAndUserId(id, currentUser.get().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelScan(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<Scan> scanOptional = scanRepository.findById(id);
        if (scanOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Scan scan = scanOptional.get();
        if (!belongsToUser(scan, currentUser.get())) {
            return ResponseEntity.notFound().build();
        }

        if (!"COMPLETED".equals(scan.getStatus()) && !"FAILED".equals(scan.getStatus()) && !"CANCELLED".equals(scan.getStatus())) {
            scan.setStatus("CANCELLED");
            scan.setCompletedAt(LocalDateTime.now());
            scan.setPauseRequested(Boolean.FALSE);
            scan.setPausedAt(null);
            scan.setUpdatedAt(LocalDateTime.now());
            scanRepository.save(scan);
            toolExecutionService.stopActiveProcesses(scan.getId());
            eventPublisher.publishScanEvent(scan.getId(), "SCAN_CANCELLED", null);
            Map<String, Object> statusPayload = new HashMap<>();
            statusPayload.put("status", scan.getStatus());
            statusPayload.put("progress", scan.getProgress() == null ? 0 : scan.getProgress());
            statusPayload.put("currentStageOrder", scan.getCurrentStageOrder());
            statusPayload.put("message", "Scan cancelled.");
            eventPublisher.publishScanStatus(scan.getId(), statusPayload);
        }

        return buildScanResponse(scan.getId(), currentUser.get().getId());
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pauseScan(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<Scan> scanOptional = scanRepository.findByIdAndUserId(id, currentUser.get().getId());
        if (scanOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Scan scan = scanOptional.get();
        String normalizedStatus = String.valueOf(scan.getStatus()).toUpperCase();
        if ("PAUSED".equals(normalizedStatus)) {
            return buildScanResponse(scan.getId(), currentUser.get().getId());
        }
        if (!"QUEUED".equals(normalizedStatus) && !"RUNNING".equals(normalizedStatus) && !"PAUSING".equals(normalizedStatus)) {
            return ResponseEntity.status(409).body(Map.of("message", "Only queued or running scans can be paused."));
        }

        eventPublisher.publishScanEvent(scan.getId(), "PAUSE_REQUESTED", null, scan.getCurrentStageOrder());

        if ("QUEUED".equals(normalizedStatus)) {
            scan.setStatus("PAUSED");
            scan.setPauseRequested(Boolean.FALSE);
            scan.setResumeStageOrder(scan.getResumeStageOrder() == null ? 1 : scan.getResumeStageOrder());
            scan.setPausedAt(LocalDateTime.now());
            scan.setUpdatedAt(LocalDateTime.now());
            scanRepository.save(scan);
            eventPublisher.publishScanEvent(scan.getId(), "SCAN_PAUSED", "Scan paused before execution started.", scan.getResumeStageOrder());
            Map<String, Object> statusPayload = new HashMap<>();
            statusPayload.put("status", scan.getStatus());
            statusPayload.put("progress", scan.getProgress() == null ? 0 : scan.getProgress());
            statusPayload.put("currentStageOrder", scan.getResumeStageOrder());
            statusPayload.put("message", "Scan paused.");
            eventPublisher.publishScanStatus(scan.getId(), statusPayload);
            return ResponseEntity.ok(scan);
        }

        scan.setStatus("PAUSING");
        scan.setPauseRequested(Boolean.TRUE);
        scan.setUpdatedAt(LocalDateTime.now());
        scanRepository.save(scan);
        toolExecutionService.stopActiveProcesses(scan.getId());
        return buildScanResponse(scan.getId(), currentUser.get().getId());
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumeScan(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<Scan> scanOptional = scanRepository.findByIdAndUserId(id, currentUser.get().getId());
        if (scanOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Scan scan = scanOptional.get();
        if (!"PAUSED".equalsIgnoreCase(scan.getStatus())) {
            return ResponseEntity.status(409).body(Map.of("message", "Only paused scans can be resumed."));
        }
        if (scanRepository.existsByUserIdAndStatusInAndIdNot(currentUser.get().getId(), ACTIVE_SCAN_STATUSES, scan.getId())) {
            return ResponseEntity.status(409).body(Map.of(
                    "message",
                    "A scan is already running. Let it complete or cancel it before starting another."
            ));
        }

        try {
            tierRuntimeAvailabilityService.assertTierAvailable(ScanTier.fromTargetValue(scan.getTier() != null ? scan.getTier() : scan.getProfileType()));
        } catch (Exception exception) {
            return ResponseEntity.status(409).body(Map.of("message", TierRuntimeAvailabilityService.USER_SAFE_TIER_UNAVAILABLE_MESSAGE));
        }

        scan.setStatus("QUEUED");
        scan.setPauseRequested(Boolean.FALSE);
        scan.setResumeStageOrder(scan.getResumeStageOrder() == null ? 1 : scan.getResumeStageOrder());
        scan.setPausedAt(null);
        scan.setUpdatedAt(LocalDateTime.now());
        Scan savedScan = scanRepository.save(scan);
        scanProducer.sendScanJob(savedScan.getId());
        return buildScanResponse(savedScan.getId(), currentUser.get().getId());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteScan(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<Scan> scanOptional = scanRepository.findByIdAndUserId(id, currentUser.get().getId());
        if (scanOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Scan scan = scanOptional.get();
        String normalizedStatus = String.valueOf(scan.getStatus()).toUpperCase();
        if ("QUEUED".equals(normalizedStatus) || "RUNNING".equals(normalizedStatus) || "PAUSING".equals(normalizedStatus)) {
            return ResponseEntity.status(409).body(Map.of("message", "Cancel the scan before deleting it."));
        }

        findingRepository.deleteByScanId(scan.getId());
        notificationRepository.deleteByScanIdAndUserId(scan.getId(), currentUser.get().getId());
        scanRepository.delete(scan);
        return ResponseEntity.ok(Map.of("message", "Scan deleted successfully."));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> getScanActivity(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        if (scanRepository.findByIdAndUserId(id, currentUser.get().getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(scanActivityService.getActivityForUser(id, currentUser.get().getId()));
    }

    @GetMapping("/compare")
    public ResponseEntity<?> compareScans(@RequestParam Long scan1, @RequestParam Long scan2, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Scan s1 = scanRepository.findById(scan1).orElse(null);
        Scan s2 = scanRepository.findById(scan2).orElse(null);
        if (s1 == null || s2 == null) return ResponseEntity.notFound().build();
        if (!belongsToUser(s1, currentUser.get()) || !belongsToUser(s2, currentUser.get())) {
            return ResponseEntity.notFound().build();
        }
        if (!s1.getTarget().getId().equals(s2.getTarget().getId())) {
            return ResponseEntity.badRequest().body("Scans must belong to the same target for comparison.");
        }

        List<Finding> allTargetFindings = findingRepository.findByTargetId(s1.getTarget().getId());

        List<Finding> scan1Findings = allTargetFindings.stream()
                .filter(f -> !f.getFirstSeenAt().isAfter(s1.getCreatedAt()) && !f.getLastSeenAt().isBefore(s1.getCreatedAt()))
                .toList();

        List<Finding> scan2Findings = allTargetFindings.stream()
                .filter(f -> !f.getFirstSeenAt().isAfter(s2.getCreatedAt()) && !f.getLastSeenAt().isBefore(s2.getCreatedAt()))
                .toList();

        List<Finding> newFindings = scan2Findings.stream()
                .filter(f -> !scan1Findings.contains(f))
                .toList();

        List<Finding> resolvedFindings = scan1Findings.stream()
                .filter(f -> !scan2Findings.contains(f))
                .toList();

        List<Finding> unchangedFindings = scan1Findings.stream()
                .filter(scan2Findings::contains)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("scan1", s1);
        result.put("scan2", s2);
        result.put("newFindings", newFindings);
        result.put("resolvedFindings", resolvedFindings);
        result.put("unchangedFindings", unchangedFindings);

        return ResponseEntity.ok(result);
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }

    private boolean belongsToUser(Target target, User user) {
        return target.getUser() != null && target.getUser().getId().equals(user.getId());
    }

    private String resolveTargetScanName(Target target) {
        if (target == null) {
            return "Target";
        }
        if (target.getName() != null && !target.getName().isBlank()) {
            return target.getName().trim();
        }
        if (target.getBaseUrl() != null && !target.getBaseUrl().isBlank()) {
            return target.getBaseUrl().trim();
        }
        return "Target";
    }

    private boolean belongsToUser(Scan scan, User user) {
        return scan.getUser() != null && scan.getUser().getId().equals(user.getId());
    }

    private ResponseEntity<?> buildScanResponse(Long scanId, Long userId) {
        return scanRepository.findWithContextByIdAndUserId(scanId, userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("id", scanId)));
    }

    private String toLegacyProfile(String tier) {
        return switch (ScanTier.fromTargetValue(tier)) {
            case FAST -> "QUICK";
            case DEEP -> "COMPREHENSIVE";
            case MEDIUM -> "STANDARD";
        };
    }
}
