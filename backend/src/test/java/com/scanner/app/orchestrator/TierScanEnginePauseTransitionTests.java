package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.NotificationService;
import com.scanner.app.websocket.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierScanEnginePauseTransitionTests {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private TierPlanRegistry tierPlanRegistry;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Mock
    private LightCrawlerService lightCrawlerService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private NormalizedReportService normalizedReportService;

    @Mock
    private TierRuntimeAvailabilityService tierRuntimeAvailabilityService;

    @Mock
    private ZapDaemonManager zapDaemonManager;

    @Test
    void pauseRequestedDuringLongRunningStepTransitionsWorkerToPaused() throws Exception {
        User user = new User();
        user.setId(10L);
        user.setEmail("pause-test@example.test");
        user.setRole("USER");
        user.setStatus("ACTIVE");

        Target target = new Target();
        target.setId(20L);
        target.setUser(user);
        target.setName("Pause Transition Target");
        target.setBaseUrl("http://127.0.0.1:3000/");
        target.setDefaultTier("FAST");
        target.setTimeoutsEnabled(Boolean.TRUE);

        Scan scan = new Scan();
        scan.setId(30L);
        scan.setUser(user);
        scan.setTarget(target);
        scan.setName(target.getName());
        scan.setTier("FAST");
        scan.setProfileType("QUICK");
        scan.setStatus("QUEUED");
        scan.setProgress(0);
        scan.setPauseRequested(Boolean.FALSE);
        scan.setResumeStageOrder(1);
        scan.setCreatedAt(LocalDateTime.now());
        scan.setUpdatedAt(LocalDateTime.now());

        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch stepInterrupted = new CountDownLatch(1);
        AtomicReference<Scan> storedScan = new AtomicReference<>(copyScan(scan));

        ToolStepExecutor blockingExecutor = new ToolStepExecutor() {
            @Override
            public String getExecutorName() {
                return "blocking-httpx";
            }

            @Override
            public boolean supports(PlanStep step) {
                return "httpx".equals(step.key());
            }

            @Override
            public StepExecutionResult execute(Scan ignoredScan, PlanStep ignoredStep, ScanExecutionContext ignoredContext, EventPublisher ignoredPublisher) throws Exception {
                stepStarted.countDown();
                try {
                    while (true) {
                        Thread.sleep(1_000);
                    }
                } catch (InterruptedException interruptedException) {
                    stepInterrupted.countDown();
                    throw interruptedException;
                }
            }
        };

        TierPlan plan = new TierPlan(
                ScanTier.FAST,
                null,
                List.of(new PlanStage(
                        1,
                        "Baseline",
                        List.of(
                                new PlanStep("normalize-url", "Normalize URL", 10, null, false, Map.of()),
                                new PlanStep("httpx", "Baseline Metadata", 20, null, false, Map.of())
                        )
                ))
        );

        when(tierPlanRegistry.planFor(ScanTier.FAST, true)).thenReturn(plan);
        when(scanRepository.findWithContextById(scan.getId())).thenAnswer(invocation -> Optional.of(copyScan(storedScan.get())));
        when(scanRepository.findById(scan.getId())).thenAnswer(invocation -> Optional.of(copyScan(storedScan.get())));
        when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> {
            Scan candidate = invocation.getArgument(0);
            Scan persisted = copyScan(candidate);
            storedScan.set(persisted);
            return copyScan(persisted);
        });
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(toolExecutionService.normalizeTargetUrl(anyString())).thenReturn(target.getBaseUrl());
        doNothing().when(tierRuntimeAvailabilityService).assertTierAvailable(ScanTier.FAST);

        TierScanEngine engine = new TierScanEngine(
                scanRepository,
                tierPlanRegistry,
                List.of(blockingExecutor),
                toolExecutionService,
                lightCrawlerService,
                notificationService,
                userRepository,
                eventPublisher,
                normalizedReportService,
                tierRuntimeAvailabilityService,
                zapDaemonManager,
                new ObjectMapper()
        );

        Thread workerThread = new Thread(() -> engine.runScan(scan.getId()), "tier-scan-pause-test");
        workerThread.start();

        assertTrue(stepStarted.await(10, TimeUnit.SECONDS), "The long-running step never started.");

        Scan pausingScan = copyScan(storedScan.get());
        pausingScan.setStatus("PAUSING");
        pausingScan.setPauseRequested(Boolean.TRUE);
        pausingScan.setUpdatedAt(LocalDateTime.now());
        storedScan.set(pausingScan);

        assertTrue(stepInterrupted.await(10, TimeUnit.SECONDS), "The running step was not interrupted.");

        workerThread.join(10_000);
        assertFalse(workerThread.isAlive(), "The scan worker did not stop after the pause request.");

        Scan finalScan = storedScan.get();
        assertEquals("PAUSED", finalScan.getStatus());
        assertFalse(Boolean.TRUE.equals(finalScan.getPauseRequested()));
        assertNotNull(finalScan.getPausedAt());
        assertEquals(1, finalScan.getResumeStageOrder());
        assertEquals(1, finalScan.getCurrentStageOrder());
        verify(toolExecutionService, atLeastOnce()).stopActiveProcesses(scan.getId());
    }

    private Scan copyScan(Scan source) {
        Scan copy = new Scan();
        copy.setId(source.getId());
        copy.setUser(source.getUser());
        copy.setTarget(source.getTarget());
        copy.setName(source.getName());
        copy.setTier(source.getTier());
        copy.setProfileType(source.getProfileType());
        copy.setStatus(source.getStatus());
        copy.setProgress(source.getProgress());
        copy.setPauseRequested(source.getPauseRequested());
        copy.setResumeStageOrder(source.getResumeStageOrder());
        copy.setCurrentStageOrder(source.getCurrentStageOrder());
        copy.setPausedAt(source.getPausedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setStartedAt(source.getStartedAt());
        copy.setCompletedAt(source.getCompletedAt());
        copy.setExecutionContextJson(source.getExecutionContextJson());
        copy.setNormalizedReportJson(source.getNormalizedReportJson());
        copy.setTimeoutsEnabled(source.getTimeoutsEnabled());
        return copy;
    }
}
