package com.example.hibernatelab.g14_lead.i3_fixed;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface I3FixedJobRepository extends JpaRepository<I3FixedJob, Long> {

	/**
	 * Khoá ghi + {@code SKIP LOCKED} (G11-S09a): dòng đang bị worker khác khoá thì bị BỎ QUA, lấy dòng kế tiếp.
	 * Hint {@code jakarta.persistence.lock.timeout = -2} là giá trị đặc biệt của Hibernate cho SKIP LOCKED.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select j from G14I3F_Job j where j.status = 'NEW' order by j.id")
	List<I3FixedJob> findNewJobsSkipLocked(Limit limit);
}
