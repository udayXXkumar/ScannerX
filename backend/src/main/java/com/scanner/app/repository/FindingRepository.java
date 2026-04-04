package com.scanner.app.repository;

import com.scanner.app.domain.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByScanId(Long scanId);
    List<Finding> findByScanIdOrderByCreatedAtDesc(Long scanId);
    List<Finding> findByTargetId(Long targetId);

    List<Finding> findTop5ByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(f) FROM Finding f WHERE f.createdAt >= :startDate")
    long countFindingsSince(@Param("startDate") LocalDateTime startDate);

    // Deduplication method
    @Query("SELECT f FROM Finding f WHERE f.target.id = :targetId AND f.title = :title AND f.affectedUrl = :affectedUrl")
    List<Finding> findExistingFindings(@Param("targetId") Long targetId,
                                     @Param("title") String title,
                                     @Param("affectedUrl") String affectedUrl);

    @Query("""
        SELECT f
        FROM Finding f
        JOIN FETCH f.scan s
        JOIN FETCH f.target t
        WHERE s.user.id = :userId
          AND (:targetId IS NULL OR t.id = :targetId)
          AND (:scanId IS NULL OR s.id = :scanId)
          AND (:completedOnly = false OR UPPER(s.status) = 'COMPLETED')
          AND LOWER(COALESCE(f.toolName, '')) <> 'engine'
          AND LOWER(COALESCE(f.category, '')) <> 'execution'
        ORDER BY f.createdAt DESC
        """)
    List<Finding> findVisibleFindings(
            @Param("userId") Long userId,
            @Param("targetId") Long targetId,
            @Param("scanId") Long scanId,
            @Param("completedOnly") boolean completedOnly
    );

    @Query("""
        SELECT f
        FROM Finding f
        JOIN f.scan s
        WHERE s.user.id = :userId
          AND (:targetId IS NULL OR f.target.id = :targetId)
          AND (:scanId IS NULL OR s.id = :scanId)
          AND (:completedOnly = false OR UPPER(s.status) = 'COMPLETED')
          AND LOWER(COALESCE(f.toolName, '')) <> 'engine'
          AND LOWER(COALESCE(f.category, '')) <> 'execution'
          AND f.createdAt >= :startInclusive
          AND f.createdAt < :endExclusive
        """)
    List<Finding> findVisibleFindingsInRange(
            @Param("userId") Long userId,
            @Param("targetId") Long targetId,
            @Param("scanId") Long scanId,
            @Param("completedOnly") boolean completedOnly,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
        SELECT f
        FROM Finding f
        JOIN f.scan s
        WHERE f.id = :id
          AND s.user.id = :userId
          AND LOWER(COALESCE(f.toolName, '')) <> 'engine'
          AND LOWER(COALESCE(f.category, '')) <> 'execution'
        """)
    Optional<Finding> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    long countByScanId(Long scanId);

    void deleteByScanId(Long scanId);
}
