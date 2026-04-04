package com.scanner.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.User;
import com.scanner.app.orchestrator.TierRuntimeAvailabilityService;
import com.scanner.app.orchestrator.ToolExecutionService;
import com.scanner.app.queue.ScanProducer;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private ScanProducer scanProducer;

    @MockitoBean
    private TierRuntimeAvailabilityService tierRuntimeAvailabilityService;

    @MockitoBean
    private ToolExecutionService toolExecutionService;

    private String authToken;
    private Target target;
    private User user;

    @BeforeEach
    void setUp() {
        String email = "scan-controller-" + System.nanoTime() + "@example.test";

        User nextUser = new User();
        nextUser.setFullName("Scan Tester");
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
        doNothing().when(toolExecutionService).stopActiveProcesses(any(Long.class));
    }

    @Test
    void createScanUsesTargetNameInsteadOfClientProvidedName() throws Exception {
        mockMvc.perform(post("/api/scans")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Client Provided Name",
                                "target", Map.of("id", target.getId())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Juice Shop"))
                .andExpect(jsonPath("$.tier").value("FAST"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(scanProducer).sendScanJob(any(Long.class));
    }

    @Test
    void createScanRejectsMissingTarget() throws Exception {
        mockMvc.perform(post("/api/scans")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Ignored"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pauseScanReturnsHydratedScanResponse() throws Exception {
        Scan runningScan = new Scan();
        runningScan.setUser(user);
        runningScan.setTarget(target);
        runningScan.setName(target.getName());
        runningScan.setTier("FAST");
        runningScan.setProfileType("QUICK");
        runningScan.setStatus("RUNNING");
        runningScan.setProgress(25);
        runningScan.setPauseRequested(Boolean.FALSE);
        runningScan.setResumeStageOrder(1);
        runningScan.setCreatedAt(LocalDateTime.now());
        runningScan.setUpdatedAt(LocalDateTime.now());
        Scan savedScan = scanRepository.save(runningScan);

        mockMvc.perform(post("/api/scans/{id}/pause", savedScan.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedScan.getId()))
                .andExpect(jsonPath("$.status").value("PAUSING"))
                .andExpect(jsonPath("$.target.id").value(target.getId()))
                .andExpect(jsonPath("$.name").value(target.getName()));

        verify(toolExecutionService).stopActiveProcesses(savedScan.getId());
    }

    @Test
    void cancelScanReturnsHydratedScanResponse() throws Exception {
        Scan runningScan = new Scan();
        runningScan.setUser(user);
        runningScan.setTarget(target);
        runningScan.setName(target.getName());
        runningScan.setTier("FAST");
        runningScan.setProfileType("QUICK");
        runningScan.setStatus("RUNNING");
        runningScan.setProgress(55);
        runningScan.setPauseRequested(Boolean.FALSE);
        runningScan.setResumeStageOrder(1);
        runningScan.setCreatedAt(LocalDateTime.now());
        runningScan.setUpdatedAt(LocalDateTime.now());
        Scan savedScan = scanRepository.save(runningScan);

        mockMvc.perform(post("/api/scans/{id}/cancel", savedScan.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedScan.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.target.id").value(target.getId()))
                .andExpect(jsonPath("$.name").value(target.getName()));

        verify(toolExecutionService).stopActiveProcesses(savedScan.getId());
    }
}
