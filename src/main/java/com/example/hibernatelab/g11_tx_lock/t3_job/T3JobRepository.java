package com.example.hibernatelab.g11_tx_lock.t3_job;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

/** Repository hàng đợi việc cho G11-S09. */
public interface T3JobRepository extends JpaRepository<T3Job, Long> {

	/** KHÔNG khoá: đọc các việc NEW theo id. Hai worker cùng đọc sẽ thấy cùng việc đầu tiên (G11-S09b). */
	@Query("select j from G11T3_Job j where j.status = 'NEW' order by j.id")
	List<T3Job> findNewJobs(Limit limit);

	/**
	 * Khoá ghi + {@code SKIP LOCKED}: bỏ qua dòng đang bị worker khác khoá và lấy dòng kế tiếp (G11-S09a).
	 * Hint {@code jakarta.persistence.lock.timeout = -2} là giá trị đặc biệt của Hibernate cho SKIP_LOCKED
	 * (0 là NOWAIT, -1 là chờ vô hạn).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select j from G11T3_Job j where j.status = 'NEW' order by j.id")
	List<T3Job> findNewJobsSkipLocked(Limit limit);
}
