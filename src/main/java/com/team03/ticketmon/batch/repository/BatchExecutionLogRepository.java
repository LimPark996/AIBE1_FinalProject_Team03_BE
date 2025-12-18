package com.team03.ticketmon.batch.repository;

import com.team03.ticketmon.batch.domain.BatchExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchExecutionLogRepository extends JpaRepository<BatchExecutionLog, Long> {
    List<BatchExecutionLog> findTop20ByOrderByStartedAtDesc();
    List<BatchExecutionLog> findByJobNameOrderByStartedAtDesc(String jobName);
}