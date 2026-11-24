package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng nhật ký, dùng ở G11-S02..S04 để chứng minh "việc đã commit hay đã rollback".
 *
 * BIẾN THỂ : T1 (g11_t1_baseline)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : không có (chỉ là bảng để đếm dòng)
 */
@Entity(name = "G11T1_AuditLog") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t1_baseline", name = "audit_log")
public class T1AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t1_audit_log_seq")
	@SequenceGenerator(name = "g11t1_audit_log_seq", schema = "g11_t1_baseline", sequenceName = "audit_log_seq", allocationSize = 50)
	private Long id;

	@Column(name = "message", nullable = false)
	private String message;

	protected T1AuditLog() {
		// JPA bắt buộc có constructor không tham số
	}

	public T1AuditLog(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
