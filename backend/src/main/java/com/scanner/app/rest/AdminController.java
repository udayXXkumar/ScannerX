package com.scanner.app.rest;

import com.scanner.app.domain.User;
import com.scanner.app.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(u -> u.setPasswordHash(null));
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id, @RequestParam String status) {
        return userRepository.findById(id).map(user -> {
            String requestedStatus = status.toUpperCase(Locale.ROOT);
            if (!"ACTIVE".equals(requestedStatus) && !"SUSPENDED".equals(requestedStatus)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Unsupported status."));
            }
            if (wouldRemoveLastActiveAdmin(user, requestedStatus, user.getRole())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "At least one active admin must remain."));
            }
            user.setStatus(requestedStatus);
            User saved = userRepository.save(user);
            saved.setPasswordHash(null);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestParam String role) {
        return userRepository.findById(id).map(user -> {
            String requestedRole = role.toUpperCase(Locale.ROOT);
            if (!"ADMIN".equals(requestedRole) && !"USER".equals(requestedRole)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Unsupported role."));
            }
            if (wouldRemoveLastActiveAdmin(user, user.getStatus(), requestedRole)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "At least one active admin must remain."));
            }
            user.setRole(requestedRole);
            User saved = userRepository.save(user);
            saved.setPasswordHash(null);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return deleteUserInternal(id);
    }

    @PostMapping("/users/{id}/delete")
    public ResponseEntity<?> deleteUserAlias(@PathVariable Long id) {
        return deleteUserInternal(id);
    }

    private ResponseEntity<?> deleteUserInternal(Long id) {
        return userRepository.findById(id).map(user -> {
            if (wouldRemoveLastActiveAdmin(user, "DELETED", "DELETED")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "At least one active admin must remain."));
            }
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully."));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private boolean wouldRemoveLastActiveAdmin(User user, String nextStatus, String nextRole) {
        boolean isCurrentlyActiveAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) && "ACTIVE".equalsIgnoreCase(user.getStatus());
        boolean willRemainActiveAdmin = "ADMIN".equalsIgnoreCase(nextRole) && "ACTIVE".equalsIgnoreCase(nextStatus);
        if (!isCurrentlyActiveAdmin || willRemainActiveAdmin) {
            return false;
        }
        return userRepository.countByRoleIgnoreCaseAndStatusIgnoreCase("ADMIN", "ACTIVE") <= 1;
    }
}
