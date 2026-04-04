package com.scanner.app.repository;

import com.scanner.app.domain.Scan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface ScanRepository extends JpaRepository<Scan, Long> {
    List<Scan> findByTargetId(Long targetId);
    List<Scan> findByUserId(Long userId);
    List<Scan> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Scan> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<Scan> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<String> statuses);
    boolean existsByUserIdAndStatusIn(Long userId, Collection<String> statuses);
    boolean existsByUserIdAndStatusInAndIdNot(Long userId, Collection<String> statuses, Long id);
    Optional<Scan> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"target", "target.user", "user"})
    Optional<Scan> findWithContextById(Long id);

    @EntityGraph(attributePaths = {"target", "target.user", "user"})
    Optional<Scan> findWithContextByIdAndUserId(Long id, Long userId);
}
