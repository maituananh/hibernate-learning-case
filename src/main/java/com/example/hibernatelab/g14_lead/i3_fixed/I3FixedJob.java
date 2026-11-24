package com.example.hibernatelab.g14_lead.i3_fixed;

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
 * BIẾN THỂ : I3 (fixed), schema g14_i3_fixed
 * BASELINE : I3 buggy
 * KHÁC BIỆT: entity giống hệt; khác ở cách repository lấy việc (query lấy việc dùng PESSIMISTIC_WRITE + SKIP LOCKED)
 * HẬU QUẢ  : việc đang bị worker khác khoá thì bị bỏ qua, mỗi worker nhận một việc khác nhau
 */
@Entity(name = "G14I3F_Job") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i3_fixed", name = "job")
public class I3FixedJob {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i3fixed_job_seq")
	@SequenceGenerator(name = "g14i3fixed_job_seq", schema = "g14_i3_fixed", sequenceName = "job_seq", allocationSize = 50)
	private Long id;

	@Column(name = "recipient", nullable = false)
	private String recipient;

	// NEW / DONE (String, không dùng enum; enum là chủ đề G12).
	@Column(name = "status", nullable = false)
	private String status;

	protected I3FixedJob() {
		// JPA bắt buộc có constructor không tham số
	}

	public I3FixedJob(String recipient) {
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
