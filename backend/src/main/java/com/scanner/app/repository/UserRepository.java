package com.scanner.app.repository;

import com.scanner.app.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    Optional<User> findByEmailVerificationToken(String emailVerificationToken);
    long countByRoleIgnoreCaseAndStatusIgnoreCase(String role, String status);
}
