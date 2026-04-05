package com.scanner.app;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceSummaryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String authToken;
    private User user;
    private Target target;

    @BeforeEach
    void setUp() {
        String email = "workspace-summary-" + System.nanoTime() + "@example.test";

        User nextUser = new User();
        nextUser.setFullName("Workspace Summary Tester");
        nextUser.setEmail(email);
        nextUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        nextUser.setRole("USER");
        nextUser.setStatus("ACTIVE");
        nextUser.setCreatedAt(LocalDateTime.now());
        nextUser.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(nextUser);

        Target nextTarget = new Target();
        nextTarget.setUser(user);
        nextTarget.setName("Juice Shop");
        nextTarget.setBaseUrl("http://127.0.0.1:3000/");
        nextTarget.setDomain("127.0.0.1");
        nextTarget.setVerificationStatus("ACTIVE");
        nextTarget.setDefaultTier("FAST");
        nextTarget.setTimeoutsEnabled(Boolean.TRUE);
        nextTarget.setCreatedAt(LocalDateTime.now());
        nextTarget.setUpdatedAt(LocalDateTime.now());
        target = targetRepository.save(nextTarget);

        authToken = jwtUtil.generateToken(email);
    }

    @Test
    void dashboardUsesLiveWorkspaceStateWhileReportsStayCompletedOnly() throws Exception {
        Scan completedScan = createScan("COMPLETED", 100, 1);
        completedScan.setCompletedAt(LocalDateTime.now().minusMinutes(5));
        scanRepository.save(completedScan);

        Scan runningScan = createScan("RUNNING", 48, 2);
        scanRepository.save(runningScan);

        createFinding(completedScan, "Completed finding", "HIGH", "OPEN");
        createFinding(runningScan, "Live finding", "MEDIUM", "OPEN");

        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + authToken)
                        .param("targetId", String.valueOf(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTargets").value(1))
                .andExpect(jsonPath("$.totalScans").value(2))
                .andExpect(jsonPath("$.totalFindings").value(2))
                .andExpect(jsonPath("$.latestFindings[0].title").exists());

        mockMvc.perform(get("/api/reports/summary")
                        .header("Authorization", "Bearer " + authToken)
                        .param("targetId", String.valueOf(target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTargets").value(1))
                .andExpect(jsonPath("$.totalScans").value(1))
                .andExpect(jsonPath("$.totalFindings").value(1))
                .andExpect(jsonPath("$.scopeLabel").value("Juice Shop · All completed runs"));
    }

    private Scan createScan(String status, int progress, int resumeStageOrder) {
        Scan scan = new Scan();
        scan.setUser(user);
        scan.setTarget(target);
        scan.setName(target.getName());
        scan.setTier("FAST");
        scan.setProfileType("QUICK");
        scan.setStatus(status);
        scan.setProgress(progress);
        scan.setPauseRequested(Boolean.FALSE);
        scan.setResumeStageOrder(resumeStageOrder);
        scan.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        scan.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return scan;
    }

    private void createFinding(Scan scan, String title, String severity, String status) {
        Finding finding = new Finding();
        finding.setScan(scan);
        finding.setTarget(target);
        finding.setTitle(title);
        finding.setSeverity(severity);
        finding.setStatus(status);
        finding.setAffectedUrl(target.getBaseUrl());
        finding.setDescription(title + " description");
        finding.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        findingRepository.save(finding);
    }
}
