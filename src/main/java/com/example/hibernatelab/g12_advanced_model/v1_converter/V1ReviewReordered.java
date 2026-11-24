package com.example.hibernatelab.g12_advanced_model.v1_converter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đánh giá có trạng thái là enum. Lưu theo số thứ tự, enum đã bị đổi thứ tự.
 *
 * BIẾN THỂ : V1ReviewReordered (ORDINAL, enum đã đổi thứ tự)
 * BASELINE : V1ReviewOrdinal (enum ORDINAL, thứ tự NEW, PAID)
 * KHÁC BIỆT: enum đã đổi thứ tự khai báo (PAID trước NEW)
 * HẬU QUẢ  : cùng số 1 trong DB nay được đọc thành NEW thay vì PAID, KHÔNG có lỗi nào (G12-S11c)
 */
@Entity(name = "G12V1ReviewReordered")
@Table(schema = "g12_v1_converter", name = "v1_review_reordered")
public class V1ReviewReordered {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12v1_review_reordered_seq")
	@SequenceGenerator(name = "g12v1_review_reordered_seq", schema = "g12_v1_converter", sequenceName = "v1_review_reordered_seq", allocationSize = 50)
	private Long id;

	@Enumerated(EnumType.ORDINAL)
	@Column(name = "status", nullable = false)
	private ReorderedReviewStatus status;

	protected V1ReviewReordered() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1ReviewReordered(ReorderedReviewStatus status) {
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public ReorderedReviewStatus getStatus() {
		return status;
	}
}
