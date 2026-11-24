package com.example.hibernatelab.g11_tx_lock.t3_job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Một việc trong hàng đợi (bảng {@code job}): worker lấy việc NEW, đánh dấu DONE.
 *
 * BIẾN THỂ : T3 (g11_t3_job)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: model khác hẳn (hàng đợi việc thay vì tồn kho), không có @Version
 * HẬU QUẢ  : nếu hai worker cùng "đọc việc NEW đầu tiên" mà không khoá thì cùng lấy một việc (G11-S09b);
 *            khoá {@code SKIP LOCKED} thì mỗi worker lấy một việc khác nhau (G11-S09a)
 */
@Entity(name = "G11T3_Job") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t3_job", name = "job")
public class T3Job {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t3_job_seq")
	@SequenceGenerator(name = "g11t3_job_seq", schema = "g11_t3_job", sequenceName = "job_seq", allocationSize = 50)
	private Long id;

	@Column(name = "payload", nullable = false)
	private String payload;

	// NEW / DONE (String, không dùng enum; enum là chủ đề G12).
	@Column(name = "status", nullable = false)
	private String status;

	// Worker nào đã xử lý, để test thấy việc bị xử lý hai lần.
	@Column(name = "worker")
	private String worker;

	protected T3Job() {
		// JPA bắt buộc có constructor không tham số
	}

	public T3Job(String payload) {
		this.payload = payload;
		this.status = "NEW";
	}

	public Long getId() {
		return id;
	}

	public String getStatus() {
		return status;
	}

	public void markDoneBy(String workerName) {
		this.status = "DONE";
		this.worker = workerName;
	}
}
