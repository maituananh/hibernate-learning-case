package com.example.hibernatelab.g14_lead.i3_buggy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Một việc "gửi email" trong hàng đợi: worker lấy việc NEW, gửi, đánh dấu DONE.
 *
 * BIẾN THỂ : I3 (buggy), schema g14_i3_buggy
 * BASELINE : I3 buggy
 * KHÁC BIỆT: entity giống hệt; khác ở cách repository lấy việc (query lấy việc không khoá)
 * HẬU QUẢ  : hai worker cùng đọc 'việc NEW đầu tiên' và cùng nhận việc đó: email gửi hai lần
 */
@Entity(name = "G14I3B_Job") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i3_buggy", name = "job")
public class I3BuggyJob {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i3buggy_job_seq")
	@SequenceGenerator(name = "g14i3buggy_job_seq", schema = "g14_i3_buggy", sequenceName = "job_seq", allocationSize = 50)
	private Long id;

	@Column(name = "recipient", nullable = false)
	private String recipient;

	// NEW / DONE (String, không dùng enum; enum là chủ đề G12).
	@Column(name = "status", nullable = false)
	private String status;

	protected I3BuggyJob() {
		// JPA bắt buộc có constructor không tham số
	}

	public I3BuggyJob(String recipient) {
		this.recipient = recipient;
		this.status = "NEW";
	}

	public Long getId() {
		return id;
	}

	public String getRecipient() {
		return recipient;
	}

	public void markDone() {
		this.status = "DONE";
	}
}
