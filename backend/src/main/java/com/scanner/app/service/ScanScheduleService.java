package com.scanner.app.service;

import com.scanner.app.domain.ScanSchedule;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.Scan;
import com.scanner.app.orchestrator.ScanTier;
import com.scanner.app.orchestrator.TierRuntimeAvailabilityService;
import com.scanner.app.repository.ScanScheduleRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.queue.ScanProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ScanScheduleService {
    private static final Logger log = LoggerFactory.getLogger(ScanScheduleService.class);
    private static final List<String> ACTIVE_SCAN_STATUSES = List.of("QUEUED", "RUNNING", "PAUSING");
    
    private final ScanScheduleRepository scheduleRepository;
    private final TargetRepository targetRepository;
    private final ScanRepository scanRepository;
    private final ScanProducer scanProducer;
    private final NotificationService notificationService;
    private final TierRuntimeAvailabilityService tierRuntimeAvailabilityService;
    private final ThreadPoolTaskScheduler taskScheduler;
    
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ScanScheduleService(ScanScheduleRepository scheduleRepository, 
                               TargetRepository targetRepository, 
                               ScanRepository scanRepository,
                               ScanProducer scanProducer,
                               NotificationService notificationService,
                               TierRuntimeAvailabilityService tierRuntimeAvailabilityService) {
        this.scheduleRepository = scheduleRepository;
        this.targetRepository = targetRepository;
        this.scanRepository = scanRepository;
        this.scanProducer = scanProducer;
        this.notificationService = notificationService;
        this.tierRuntimeAvailabilityService = tierRuntimeAvailabilityService;
        
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(5);
        this.taskScheduler.setThreadNamePrefix("ScanScheduler-");
        this.taskScheduler.initialize();
    }

    @PostConstruct
    public void initSchedules() {
        List<ScanSchedule> activeSchedules = scheduleRepository.findByStatus("ACTIVE");
        for (ScanSchedule schedule : activeSchedules) {
            try {
                scheduleTargetScan(schedule);
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping schedule {} due to invalid cron expression: {}", schedule.getId(), schedule.getCronExpression());
                schedule.setStatus("PAUSED");
                scheduleRepository.save(schedule);
            }
        }
    }

    public ScanSchedule createSchedule(Long targetId, String cron) {
        Target target = targetRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Target not found"));
        String normalizedCron = normalizeCronExpression(cron);
        
        ScanSchedule schedule = new ScanSchedule();
        schedule.setTarget(target);
        schedule.setCronExpression(normalizedCron);
        schedule.setScanProfile(ScanTier.fromTargetValue(target.getDefaultTier()).toDisplayLabel());
        schedule.setStatus("ACTIVE");
        
        schedule = scheduleRepository.save(schedule);
        scheduleTargetScan(schedule);
        return schedule;
    }

    public void cancelSchedule(Long scheduleId) {
        ScanSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule != null) {
            schedule.setStatus("PAUSED");
            scheduleRepository.save(schedule);
            
            ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private void scheduleTargetScan(ScanSchedule schedule) {
        Runnable task = () -> {
            log.info("Executing scheduled scan for Target ID: {}", schedule.getTarget().getId());
            try {
                if (scanRepository.existsByUserIdAndStatusIn(schedule.getTarget().getUser().getId(), ACTIVE_SCAN_STATUSES)) {
                    notificationService.createNotification(
                            schedule.getTarget().getUser(),
                            "Scheduled Scan Skipped",
                            "A scheduled scan for " + resolveTargetScanName(schedule.getTarget()) + " was skipped because another scan is already running.",
                            "WARNING",
                            null,
                            schedule.getTarget().getId(),
                            null
                    );
                    return;
                }

                ScanTier tier = ScanTier.fromTargetValue(schedule.getTarget().getDefaultTier());
                try {
                    tierRuntimeAvailabilityService.assertTierAvailable(tier);
                } catch (Exception exception) {
                    notificationService.createNotification(
                            schedule.getTarget().getUser(),
                            "Scheduled Scan Unavailable",
                            TierRuntimeAvailabilityService.USER_SAFE_TIER_UNAVAILABLE_MESSAGE,
                            "WARNING",
                            null,
                            schedule.getTarget().getId(),
                            null
                    );
                    return;
                }

                Scan scan = new Scan();
                scan.setUser(schedule.getTarget().getUser());
                scan.setTarget(schedule.getTarget());
                scan.setName(resolveTargetScanName(schedule.getTarget()));
                String persistedTier = tier.toPersistedValue();
                scan.setTier(persistedTier);
                scan.setTimeoutsEnabled(schedule.getTarget().getTimeoutsEnabled() == null ? Boolean.TRUE : schedule.getTarget().getTimeoutsEnabled());
                scan.setProfileType(toLegacyProfile(persistedTier));
                scan.setStatus("QUEUED");
                scan.setProgress(0);
                scan.setPauseRequested(Boolean.FALSE);
                scan.setCurrentStageOrder(null);
                scan.setResumeStageOrder(1);
                scan.setExecutionContextJson(null);
                scan.setNormalizedReportJson(null);
                scan.setCreatedAt(LocalDateTime.now());
                scan.setUpdatedAt(LocalDateTime.now());
                Scan savedScan = scanRepository.save(scan);
                
                scanProducer.sendScanJob(savedScan.getId());
                
                schedule.setLastRunAt(LocalDateTime.now());
                scheduleRepository.save(schedule);
            } catch (Exception e) {
                log.error("Failed to execute scheduled scan", e);
            }
        };

        String normalizedCron = normalizeCronExpression(schedule.getCronExpression());
        if (!normalizedCron.equals(schedule.getCronExpression())) {
            schedule.setCronExpression(normalizedCron);
            scheduleRepository.save(schedule);
        }

        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(normalizedCron));
        scheduledTasks.put(schedule.getId(), future);
    }

    private String normalizeCronExpression(String cronExpression) {
        if (cronExpression == null) {
            throw new IllegalArgumentException("Cron expression is required");
        }

        String normalized = cronExpression.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cron expression is required");
        }

        String[] parts = normalized.split(" ");
        if (parts.length == 5) {
            return "0 " + normalized;
        }

        if (parts.length == 6) {
            return normalized;
        }

        throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
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

    private String toLegacyProfile(String tier) {
        return switch (ScanTier.fromTargetValue(tier)) {
            case FAST -> "QUICK";
            case DEEP -> "COMPREHENSIVE";
            case MEDIUM -> "STANDARD";
        };
    }
}
