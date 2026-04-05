package com.scanner.app;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.FindingEnrichmentService;
import com.scanner.app.service.HuggingFaceInferenceClient;
import com.scanner.app.websocket.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "AI_FINDING_ENRICHMENT_ENABLED=true",
        "HF_API_TOKEN=test-token",
        "AI_ENRICHMENT_MAX_RETRIES=0"
})
@ActiveProfiles("test")
class FindingEnrichmentServiceIntegrationTests {

    @Autowired
    private FindingEnrichmentService findingEnrichmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FindingRepository findingRepository;

    @MockitoBean
    private HuggingFaceInferenceClient huggingFaceInferenceClient;

    @MockitoBean
    private EventPublisher eventPublisher;

    private Scan scan;
    private Target target;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFullName("AI Finding Tester");
        user.setEmail("finding-ai-" + System.nanoTime() + "@example.test");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        Target nextTarget = new Target();
        nextTarget.setUser(savedUser);
        nextTarget.setName("Juice Shop");
        nextTarget.setBaseUrl("http://127.0.0.1:3000/");
        nextTarget.setDomain("127.0.0.1");
        nextTarget.setVerificationStatus("ACTIVE");
        nextTarget.setDefaultTier("FAST");
        nextTarget.setTimeoutsEnabled(Boolean.TRUE);
        nextTarget.setCreatedAt(LocalDateTime.now());
        nextTarget.setUpdatedAt(LocalDateTime.now());
        target = targetRepository.save(nextTarget);

        Scan nextScan = new Scan();
        nextScan.setUser(savedUser);
        nextScan.setTarget(target);
        nextScan.setName(target.getName());
        nextScan.setTier("FAST");
        nextScan.setProfileType("QUICK");
        nextScan.setStatus("RUNNING");
        nextScan.setProgress(12);
        nextScan.setPauseRequested(Boolean.FALSE);
        nextScan.setResumeStageOrder(1);
        nextScan.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        nextScan.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        scan = scanRepository.save(nextScan);

        when(huggingFaceInferenceClient.isConfigured()).thenReturn(true);
        when(huggingFaceInferenceClient.getResolvedModelId()).thenReturn("Qwen/Qwen2.5-7B-Instruct");
    }

    @Test
    void enrichFindingNowPersistsAiFieldsAndPublishesEvent() {
        when(huggingFaceInferenceClient.enrichFinding(any()))
                .thenReturn(new HuggingFaceInferenceClient.FindingAiEnrichmentResult(
                        "ScannerX AI summary",
                        "An attacker could abuse this weakness when exposed input reaches a vulnerable code path, which can affect confidentiality or integrity.",
                        "Qwen/Qwen2.5-7B-Instruct"
                ));

        Finding savedFinding = findingRepository.save(buildFinding("SQL Injection"));

        findingEnrichmentService.enrichFindingNow(savedFinding.getId());

        Finding reloadedFinding = findingRepository.findById(savedFinding.getId()).orElseThrow();
        assertEquals("COMPLETED", reloadedFinding.getAiEnrichmentStatus());
        assertEquals("ScannerX AI summary", reloadedFinding.getAiDescription());
        assertTrue(reloadedFinding.getExploitNarrative().contains("attacker could abuse"));
        assertEquals("Qwen/Qwen2.5-7B-Instruct", reloadedFinding.getAiModel());
        assertNotNull(reloadedFinding.getAiEnrichedAt());

        verify(eventPublisher).publishScanEvent(eq(scan.getId()), eq("FINDING_ENRICHED"), any(Finding.class));
    }

    @Test
    void enrichFindingNowMarksFailureWithoutBreakingFindingPersistence() {
        when(huggingFaceInferenceClient.enrichFinding(any()))
                .thenThrow(new IllegalStateException("Inference provider unavailable"));

        Finding savedFinding = findingRepository.save(buildFinding("Missing Security Header"));

        findingEnrichmentService.enrichFindingNow(savedFinding.getId());

        Finding reloadedFinding = findingRepository.findById(savedFinding.getId()).orElseThrow();
        assertEquals("FAILED", reloadedFinding.getAiEnrichmentStatus());
        assertTrue(reloadedFinding.getAiEnrichmentError().contains("Inference provider unavailable"));
        assertEquals("Missing Security Header", reloadedFinding.getTitle());
        assertEquals("OPEN", reloadedFinding.getStatus());
    }

    private Finding buildFinding(String title) {
        Finding finding = new Finding();
        finding.setScan(scan);
        finding.setTarget(target);
        finding.setToolName("zap");
        finding.setCategory("Passive");
        finding.setTitle(title);
        finding.setSeverity("HIGH");
        finding.setStatus("OPEN");
        finding.setAffectedUrl(target.getBaseUrl() + "login");
        finding.setDescription(title + " raw description");
        finding.setEvidenceData("Example evidence collected by the scanner.");
        finding.setCweId("CWE-79");
        finding.setOwaspCategory("A03:2021-Injection");
        finding.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        finding.setFirstSeenAt(LocalDateTime.now().minusMinutes(1));
        finding.setLastSeenAt(LocalDateTime.now().minusMinutes(1));
        return finding;
    }
}
