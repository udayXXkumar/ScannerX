package com.scanner.app.service;

import com.scanner.app.domain.Notification;
import com.scanner.app.domain.User;
import com.scanner.app.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public boolean markAsRead(Long id, Long userId) {
        return notificationRepository.findByIdAndUserId(id, userId).map(n -> {
            n.setRead(true);
            notificationRepository.save(n);
            return true;
        }).orElse(false);
    }

    public boolean deleteNotification(Long id, Long userId) {
        return notificationRepository.findByIdAndUserId(id, userId).map(n -> {
            notificationRepository.delete(n);
            return true;
        }).orElse(false);
    }

    public void createNotification(User user, String title, String message, String type) {
        createNotification(user, title, message, type, null, null, null);
    }

    public void createNotification(User user, String title, String message, String type, Long scanId, Long targetId, Integer findingCount) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setScanId(scanId);
        n.setTargetId(targetId);
        n.setFindingCount(findingCount);
        n.setCreatedAt(LocalDateTime.now());
        n.setRead(false);
        notificationRepository.save(n);

        // Simulate Email Dispatch
        log.info("📧 [SIMULATED EMAIL] To: {} | Subject: {} | Body: {}", user.getEmail(), title, message);
    }
}
