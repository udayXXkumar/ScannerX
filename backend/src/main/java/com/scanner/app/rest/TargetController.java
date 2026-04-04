package com.scanner.app.rest;

import com.scanner.app.domain.Target;
import com.scanner.app.domain.User;
import com.scanner.app.orchestrator.ScanTier;
import com.scanner.app.repository.TargetRepository;
import com.scanner.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/targets")
public class TargetController {

    private final TargetRepository targetRepository;
    private final UserRepository userRepository;

    public TargetController(TargetRepository targetRepository, UserRepository userRepository) {
        this.targetRepository = targetRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Target>> getAllTargets(Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        List<Target> targets = targetRepository.findByUserId(currentUser.get().getId());
        boolean changed = false;

        for (Target target : targets) {
            changed = normalizeForDirectScan(target) || changed;
        }

        if (changed) {
            targetRepository.saveAll(targets);
        }

        return ResponseEntity.ok(targets);
    }

    @PostMapping
    public ResponseEntity<?> createTarget(@RequestBody Target target, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String requestedName = target.getName() == null ? "" : target.getName().trim();
        if (requestedName.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Target name is required."));
        }
        if (targetRepository.existsByUserIdAndNameIgnoreCase(currentUser.get().getId(), requestedName)) {
            return ResponseEntity.status(409).body(java.util.Map.of("message", "Target name must be unique."));
        }

        target.setUser(currentUser.get());
        target.setName(requestedName);
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        target.setDefaultTier(normalizeTier(target.getDefaultTier()));
        target.setTimeoutsEnabled(target.getTimeoutsEnabled() == null ? Boolean.TRUE : target.getTimeoutsEnabled());
        normalizeForDirectScan(target);
        
        if (target.getBaseUrl() != null) {
            try {
                java.net.URI uri = new java.net.URI(target.getBaseUrl());
                target.setDomain(uri.getHost());
            } catch (Exception e) {
                target.setDomain("unknown");
            }
        }
        
        return ResponseEntity.ok(targetRepository.save(target));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyTarget(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Target target = targetRepository.findById(id).orElse(null);
        if (target == null || !belongsToUser(target, currentUser.get())) return ResponseEntity.notFound().build();

        normalizeForDirectScan(target);
        target.setUpdatedAt(LocalDateTime.now());
        targetRepository.save(target);
        return ResponseEntity.ok(target);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTarget(@PathVariable Long id, @RequestBody Target targetDetails, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Optional<Target> targetOptional = targetRepository.findById(id);
        if (targetOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Target target = targetOptional.get();
        if (!belongsToUser(target, currentUser.get())) {
            return ResponseEntity.notFound().build();
        }

        String requestedName = targetDetails.getName() == null ? "" : targetDetails.getName().trim();
        if (requestedName.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Target name is required."));
        }
        if (targetRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(currentUser.get().getId(), requestedName, target.getId())) {
            return ResponseEntity.status(409).body(java.util.Map.of("message", "Target name must be unique."));
        }

        target.setName(requestedName);
        target.setBaseUrl(targetDetails.getBaseUrl());
        target.setDefaultTier(normalizeTier(targetDetails.getDefaultTier()));
        target.setProjectGroupName(targetDetails.getProjectGroupName());
        target.setTags(targetDetails.getTags());
        target.setUpdatedAt(LocalDateTime.now());
        
        if (targetDetails.getBaseUrl() != null) {
            try {
                java.net.URI uri = new java.net.URI(targetDetails.getBaseUrl());
                target.setDomain(uri.getHost());
            } catch (Exception e) {
                target.setDomain("unknown");
            }
        }

        normalizeForDirectScan(target);
        return ResponseEntity.ok(targetRepository.save(target));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTarget(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return targetRepository.findById(id).map(target -> {
            if (!belongsToUser(target, currentUser.get())) {
                return ResponseEntity.notFound().build();
            }

            targetRepository.delete(target);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }

    private boolean belongsToUser(Target target, User user) {
        return target.getUser() != null && target.getUser().getId().equals(user.getId());
    }

    private boolean normalizeForDirectScan(Target target) {
        boolean changed = false;

        if (!"ACTIVE".equals(target.getVerificationStatus())) {
            target.setVerificationStatus("ACTIVE");
            changed = true;
        }

        if (target.getVerificationToken() != null) {
            target.setVerificationToken(null);
            changed = true;
        }

        return changed;
    }

    private String normalizeTier(String value) {
        return ScanTier.fromTargetValue(value).toPersistedValue();
    }
}
