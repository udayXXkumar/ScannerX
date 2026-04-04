package com.scanner.app.rest;

import com.scanner.app.domain.Notification;
import com.scanner.app.domain.User;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(user.getId()));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(notificationService.getUnreadCount(user.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return notificationService.markAsRead(id, user.getId())
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return notificationService.deleteNotification(id, user.getId())
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }
}
