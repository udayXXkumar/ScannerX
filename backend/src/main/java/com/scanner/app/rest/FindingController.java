package com.scanner.app.rest;

import com.scanner.app.domain.Finding;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.domain.User;
import com.scanner.app.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/findings")
public class FindingController {

    private final FindingRepository findingRepository;
    private final UserRepository userRepository;

    public FindingController(FindingRepository findingRepository, UserRepository userRepository) {
        this.findingRepository = findingRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Finding>> getAllFindings(
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long scanId,
            @RequestParam(defaultValue = "true") boolean completedOnly,
            Authentication authentication
    ) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                findingRepository.findVisibleFindings(currentUser.get().getId(), targetId, scanId, completedOnly)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Finding> getFindingById(@PathVariable Long id, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return findingRepository.findByIdAndUserId(id, currentUser.get().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Finding> updateFinding(@PathVariable Long id, @RequestBody Finding findingDetails, Authentication authentication) {
        Optional<User> currentUser = resolveCurrentUser(authentication);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return findingRepository.findByIdAndUserId(id, currentUser.get().getId()).map(finding -> {
            if (findingDetails.getStatus() != null) {
                finding.setStatus(findingDetails.getStatus());
            }
            if (findingDetails.getAssignedUser() != null) {
                finding.setAssignedUser(findingDetails.getAssignedUser());
            }
            if (findingDetails.getComments() != null) {
                finding.setComments(findingDetails.getComments());
            }
            return ResponseEntity.ok(findingRepository.save(finding));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Optional<User> resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }
}
