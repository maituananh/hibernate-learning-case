package com.example.hibernatelab.g14_lead.i3_fixed;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Worker gửi email: nhận một việc NEW, gửi, đánh dấu DONE, trong MỘT transaction.
 *
 * BIẾN THỂ : I3 (fixed)
 * BASELINE : I3 buggy
 * KHÁC BIỆT: query lấy việc dùng PESSIMISTIC_WRITE + SKIP LOCKED
 * HẬU QUẢ  : việc đang bị worker khác khoá thì bị bỏ qua, mỗi worker nhận một việc khác nhau
 */
@Service
public class I3FixedEmailWorker {

	private final I3FixedJobRepository jobs;
	private final I3FixedEmailLogRepository emailLog;
	private final TransactionTemplate tx;

	public I3FixedEmailWorker(I3FixedJobRepository jobs, I3FixedEmailLogRepository emailLog,
		PlatformTransactionManager transactionManager) {
		this.jobs = jobs;
		this.emailLog = emailLog;
		this.tx = new TransactionTemplate(transactionManager);
	}

	public Long processNext(String workerName) {
		return processNext(workerName, () -> { });
	}

	/**
	 * Trả id việc đã xử lý, hoặc {@code null} nếu không còn việc nào.
	 * {@code afterJobRead} chỉ để TEST ép thứ tự xen kẽ giữa hai worker (cổng đồng bộ, không dùng sleep);
	 * code thật không có tham số này. Nó chạy sau khi đã nhận việc và trước khi gửi mail.
	 */
	public Long processNext(String workerName, Runnable afterJobRead) {
		return tx.execute(status -> {
			I3FixedJob job = jobs.findNewJobsSkipLocked(Limit.of(1)).stream().findFirst().orElse(null);
			if (job == null) {
				return null;
			}
			afterJobRead.run();
			emailLog.save(new I3FixedEmailLog(job.getId(), workerName)); // "gửi email"
			job.markDone(); // [FLUSH] lúc commit: update job set status='DONE'
			return job.getId();
		});
	}
}
