package com.scanner.app.repository;

import com.scanner.app.domain.ScanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ScanScheduleRepository extends JpaRepository<ScanSchedule, Long> {
    List<ScanSchedule> findByStatus(String status);

    @Query("""
        SELECT schedule
        FROM ScanSchedule schedule
        JOIN FETCH schedule.target target
        WHERE target.user.id = :userId
        ORDER BY schedule.createdAt DESC
        """)
    List<ScanSchedule> findByUserId(Long userId);

    Optional<ScanSchedule> findByIdAndTarget_User_Id(Long id, Long userId);
}
