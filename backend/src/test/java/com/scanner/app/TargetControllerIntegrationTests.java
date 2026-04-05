package com.scanner.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TargetControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String authToken;

    @BeforeEach
    void setUp() {
        String email = "targets-" + System.nanoTime() + "@example.test";

        User user = new User();
        user.setFullName("Target Tester");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        authToken = jwtUtil.generateToken(email);
    }

    @Test
    void createTargetRejectsDuplicateNamesPerUser() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "Juice Shop",
                "baseUrl", "http://127.0.0.1:3000/",
                "defaultTier", "FAST",
                "timeoutsEnabled", true
        );

        mockMvc.perform(post("/api/targets")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Juice Shop"));

        mockMvc.perform(post("/api/targets")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Target name must be unique."));
    }

    @Test
    void getTargetsDoesNotMutateVerificationFieldsOnRead() throws Exception {
        User user = userRepository.findByEmail(jwtUtil.extractUsername(authToken)).orElseThrow();

        Target target = new Target();
        target.setUser(user);
        target.setName("Legacy Target");
        target.setBaseUrl("http://127.0.0.1:3000/");
        target.setDomain("127.0.0.1");
        target.setVerificationStatus("PENDING");
        target.setVerificationToken("legacy-token");
        target.setDefaultTier("FAST");
        target.setTimeoutsEnabled(Boolean.TRUE);
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        Target savedTarget = targetRepository.save(target);

        mockMvc.perform(get("/api/targets")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationStatus").value("PENDING"));

        Target reloadedTarget = targetRepository.findById(savedTarget.getId()).orElseThrow();
        assertEquals("PENDING", reloadedTarget.getVerificationStatus());
        assertEquals("legacy-token", reloadedTarget.getVerificationToken());
        assertNull(reloadedTarget.getLastScanAt());
    }
}
