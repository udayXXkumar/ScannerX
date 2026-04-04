package com.scanner.app.rest;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TargetRepository targetRepository;
    private final ScanRepository scanRepository;
    private final FindingRepository findingRepository;
    private final UserRepository userRepository;

    public DashboardController(TargetRepository targetRepository, ScanRepository scanRepository, FindingRepository findingRepository, UserRepository userRepository) {
        this.targetRepository = targetRepository;
        this.scanRepository = scanRepository;
        this.findingRepository = findingRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getSummary(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Long userId = currentUser.get().getId();
        List<Scan> completedScans = scanRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "COMPLETED");
        List<Target> userTargets = targetRepository.findByUserId(userId);

        Long effectiveScanId = null;
        Long effectiveTargetId = targetId;

        if (effectiveTargetId != null) {
            Optional<Target> selectedTarget = targetRepository.findByIdAndUserId(effectiveTargetId, userId);
            if (selectedTarget.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Long selectedTargetId = effectiveTargetId;
            completedScans = completedScans.stream()
                    .filter(scan -> scan.getTarget() != null && scan.getTarget().getId().equals(selectedTargetId))
                    .toList();
        } else if (scanId != null) {
            Optional<Scan> selectedScan = scanRepository.findWithContextByIdAndUserId(scanId, userId);
            if (selectedScan.isEmpty() || !"COMPLETED".equalsIgnoreCase(selectedScan.get().getStatus())) {
                return ResponseEntity.notFound().build();
            }
            effectiveScanId = scanId;
            completedScans = completedScans.stream().filter(scan -> scan.getId().equals(scanId)).toList();
            effectiveTargetId = completedScans.isEmpty() ? null : completedScans.getFirst().getTarget().getId();
        }

        DashboardSummary summary = new DashboardSummary();
        if (effectiveTargetId != null) {
            summary.setTotalTargets(1);
        } else if (effectiveScanId != null) {
            summary.setTotalTargets(completedScans.stream()
                    .map(scan -> scan.getTarget() == null ? null : scan.getTarget().getId())
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count());
        } else {
            summary.setTotalTargets(userTargets.size());
        }
        summary.setTotalScans(completedScans.size());

        List<Finding> visibleFindings = findingRepository.findVisibleFindings(userId, effectiveTargetId, effectiveScanId, true);
        summary.setTotalFindings(visibleFindings.size());

        long total = summary.getTotalFindings();
        int score = (int) Math.max(10, 100 - (total * 2));
        summary.setRiskScore(score);

        List<DashboardSummary.FindingTrend> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime startOfDay = day.atStartOfDay();
            LocalDateTime endOfDay = day.plusDays(1).atStartOfDay();

            trend.add(new DashboardSummary.FindingTrend(
                day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                findingRepository.findVisibleFindingsInRange(userId, effectiveTargetId, effectiveScanId, true, startOfDay, endOfDay).size()
            ));
        }
        summary.setFindingsTrend(trend);

        summary.setLatestFindings(visibleFindings.stream().limit(5).toList());

        return ResponseEntity.ok(summary);
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }
}
