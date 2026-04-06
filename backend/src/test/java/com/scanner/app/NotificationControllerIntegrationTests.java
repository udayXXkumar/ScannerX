package com.scanner.app;

import com.scanner.app.domain.Notification;
import com.scanner.app.domain.User;
import com.scanner.app.repository.NotificationRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String authToken;
    private User user;

    @BeforeEach
    void setUp() {
        String email = "notifications-" + System.nanoTime() + "@example.test";

        user = new User();
        user.setFullName("Notification Tester");
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
    void unreadCountReturnsOkForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/notifications/unread/count")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void notificationsListReturnsOkForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void unreadCountReturnsUnauthorizedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/notifications/unread/count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteNotificationAliasDeletesOwnedNotification() throws Exception {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle("Scan finished");
        notification.setMessage("Your scan completed.");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setType("SCAN_COMPLETED");
        Notification savedNotification = notificationRepository.save(notification);

        mockMvc.perform(post("/api/notifications/{id}/delete", savedNotification.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        assertFalse(notificationRepository.findById(savedNotification.getId()).isPresent());
    }
}
