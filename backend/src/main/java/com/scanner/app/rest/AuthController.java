package com.scanner.app.rest;

import com.scanner.app.domain.User;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.security.CustomUserDetails;
import com.scanner.app.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            String token = jwtUtil.generateToken(user.getEmail());

            return ResponseEntity.ok(buildAuthResponse(user, token));
        } catch (BadCredentialsException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (AuthenticationException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "An error occurred during login");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        if (userRepository.count() == 0) {
            user.setRole("ADMIN");
        } else {
            user.setRole("USER");
        }

        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        String emailToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(emailToken);
        user.setEmailVerified(false);

        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully.");
        response.put("verificationToken", emailToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        return userRepository.findByEmailVerificationToken(token)
                .map(u -> {
                    u.setEmailVerified(true);
                    u.setEmailVerificationToken(null);
                    userRepository.save(u);
                    return ResponseEntity.ok("Email verified successfully.");
                })
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid token."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .map(u -> {
                    String resetToken = UUID.randomUUID().toString();
                    u.setResetToken(resetToken);
                    u.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
                    userRepository.save(u);

                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Password reset instructions sent.");
                    response.put("resetToken", resetToken);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.ok(Map.of("message", "If the email is registered, you will receive reset instructions.")));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return userRepository.findByResetToken(request.getToken())
                .map(u -> {
                    if (u.getResetTokenExpiry() == null || u.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Reset token has expired.");
                    }
                    u.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    u.setResetToken(null);
                    u.setResetTokenExpiry(null);
                    u.setUpdatedAt(LocalDateTime.now());
                    userRepository.save(u);
                    return ResponseEntity.ok("Password reset successfully.");
                })
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid reset token."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> ResponseEntity.ok(buildCurrentUserResponse(u)))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        if (fullName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Full name is required."));
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> {
                    u.setFullName(fullName);
                    u.setUpdatedAt(LocalDateTime.now());
                    User saved = userRepository.save(u);
                    return ResponseEntity.ok(buildCurrentUserResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> {
                    if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Current password is required.");
                    }
                    if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("New password is required.");
                    }
                    if (!passwordEncoder.matches(request.getCurrentPassword(), u.getPasswordHash())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect current password.");
                    }
                    u.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    u.setUpdatedAt(LocalDateTime.now());
                    userRepository.save(u);
                    return ResponseEntity.ok("Password updated successfully.");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteCurrentUser(Authentication authentication) {
        return deleteCurrentUserInternal(authentication);
    }

    @PostMapping("/me/delete")
    public ResponseEntity<?> deleteCurrentUserAlias(Authentication authentication) {
        return deleteCurrentUserInternal(authentication);
    }

    private ResponseEntity<?> deleteCurrentUserInternal(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    boolean isLastActiveAdmin = "ADMIN".equalsIgnoreCase(user.getRole())
                            && "ACTIVE".equalsIgnoreCase(user.getStatus())
                            && userRepository.countByRoleIgnoreCaseAndStatusIgnoreCase("ADMIN", "ACTIVE") <= 1;
                    if (isLastActiveAdmin) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("At least one active admin must remain.");
                    }
                    userRepository.delete(user);
                    return ResponseEntity.ok(Map.of("message", "Account deleted successfully."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getFullName()
        );
    }

    private CurrentUserResponse buildCurrentUserResponse(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getFullName()
        );
    }
}
