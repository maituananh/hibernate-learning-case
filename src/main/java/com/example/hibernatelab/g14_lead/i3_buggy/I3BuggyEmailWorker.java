package com.example.hibernatelab.g14_lead.i3_buggy;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Worker gửi email: nhận một việc NEW, gửi, đánh dấu DONE, trong MỘT transaction.
 *
 * BIẾN THỂ : I3 (buggy)
 * BASELINE : I3 buggy
 * KHÁC BIỆT: query lấy việc không khoá
 * HẬU QUẢ  : hai worker cùng đọc 'việc NEW đầu tiên' và cùng nhận việc đó: email gửi hai lần
 */
@Service
public class I3BuggyEmailWorker {

	private final I3BuggyJobRepository jobs;
	private final I3BuggyEmailLogRepository emailLog;
	private final TransactionTemplate tx;

	public I3BuggyEmailWorker(I3BuggyJobRepository jobs, I3BuggyEmailLogRepository emailLog,
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
			I3BuggyJob job = jobs.findNewJobs(Limit.of(1)).stream().findFirst().orElse(null);
			if (job == null) {
				return null;
			}
			afterJobRead.run();
			emailLog.save(new I3BuggyEmailLog(job.getId(), workerName)); // "gửi email"
			job.markDone(); // [FLUSH] lúc commit: update job set status='DONE'
			return job.getId();
		});
	}
}
