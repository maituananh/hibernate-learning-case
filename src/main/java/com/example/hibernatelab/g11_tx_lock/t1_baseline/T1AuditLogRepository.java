package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository của {@link T1AuditLog}; {@code save(new)} là {@code persist}. */
public interface T1AuditLogRepository extends JpaRepository<T1AuditLog, Long> {
}
