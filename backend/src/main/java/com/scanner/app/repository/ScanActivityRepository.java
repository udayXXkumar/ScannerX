package com.scanner.app.repository;

import com.scanner.app.domain.ScanActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanActivityRepository extends JpaRepository<ScanActivity, Long> {
    List<ScanActivity> findByScanIdAndScanUserIdOrderByCreatedAtAsc(Long scanId, Long userId);
}
