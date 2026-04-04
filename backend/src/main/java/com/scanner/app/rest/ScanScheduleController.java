package com.scanner.app.rest;

import com.scanner.app.domain.ScanSchedule;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.orchestrator.ScanTier;
import com.scanner.app.service.ScanScheduleService;
import com.scanner.app.repository.ScanScheduleRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/schedules")
public class ScanScheduleController {

    private final ScanScheduleService scheduleService;
    private final ScanScheduleRepository scheduleRepository;
    private final TargetRepository targetRepository;
    private final UserRepository userRepository;

    public ScanScheduleController(
            ScanScheduleService scheduleService,
            ScanScheduleRepository scheduleRepository,
            TargetRepository targetRepository,
            UserRepository userRepository
    ) {
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.targetRepository = targetRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<ScanSchedule>> getAllSchedules(Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(scheduleRepository.findByUserId(currentUser.get().getId()));
    }

    @PostMapping
    public ResponseEntity<?> createSchedule(@RequestBody Map<String, Object> payload, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Long targetId = Long.valueOf(payload.get("targetId").toString());
        String cron = payload.get("cronExpression").toString();

        Optional<Target> target = targetRepository.findByIdAndUserId(targetId, currentUser.get().getId());
        if (target.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ScanSchedule schedule = scheduleService.createSchedule(targetId, cron);
        schedule.setScanProfile(ScanTier.fromTargetValue(target.get().getDefaultTier()).toDisplayLabel());
        return ResponseEntity.ok(schedule);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelSchedule(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        if (scheduleRepository.findByIdAndTarget_User_Id(id, currentUser.get().getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        scheduleService.cancelSchedule(id);
        return ResponseEntity.ok().build();
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }
}
