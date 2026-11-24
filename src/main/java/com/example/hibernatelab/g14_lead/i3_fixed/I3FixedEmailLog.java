package com.example.hibernatelab.g14_lead.i3_fixed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Nhật ký "đã gửi email": mỗi lần worker gửi là một dòng. Một việc mà có HAI dòng nghĩa là email bị gửi hai lần.
 * (Việc gửi mail thật được thay bằng ghi dòng này để test kiểm chứng được.)
 *
 * BIẾN THỂ : I3 (fixed), schema g14_i3_fixed
 * BASELINE : I3 buggy
 * KHÁC BIỆT: giống hệt package còn lại
 * HẬU QUẢ  : không có riêng
 */
@Entity(name = "G14I3F_EmailLog")
@Table(schema = "g14_i3_fixed", name = "email_log")
public class I3FixedEmailLog {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i3fixed_log_seq")
	@SequenceGenerator(name = "g14i3fixed_log_seq", schema = "g14_i3_fixed", sequenceName = "email_log_seq", allocationSize = 50)
	private Long id;

	// Cố ý để id thường (không @ManyToOne): bài này học về khoá, không học về quan hệ.
	@Column(name = "job_id", nullable = false)
	private Long jobId;

	@Column(name = "worker", nullable = false)
	private String worker;

	protected I3FixedEmailLog() {
		// JPA bắt buộc có constructor không tham số
	}

	public I3FixedEmailLog(Long jobId, String worker) {
		this.jobId = jobId;
		this.worker = worker;
	}

	public Long getId() {
		return id;
	}
}
