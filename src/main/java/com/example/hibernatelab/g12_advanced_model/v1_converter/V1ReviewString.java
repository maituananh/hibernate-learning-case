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
 * Đánh giá có trạng thái là enum. Lưu theo tên.
 *
 * BIẾN THỂ : V1ReviewString (STRING)
 * BASELINE : V1ReviewOrdinal (enum ORDINAL, thứ tự NEW, PAID)
 * KHÁC BIỆT: cột là chữ (@Enumerated(STRING)) thay vì số
 * HẬU QUẢ  : DB lưu 'NEW'/'PAID'; đổi thứ tự enum không ảnh hưởng dữ liệu cũ, đổi TÊN hằng số thì có (G12-S11c)
 */
@Entity(name = "G12V1ReviewString")
@Table(schema = "g12_v1_converter", name = "v1_review_string")
public class V1ReviewString {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12v1_review_string_seq")
	@SequenceGenerator(name = "g12v1_review_string_seq", schema = "g12_v1_converter", sequenceName = "v1_review_string_seq", allocationSize = 50)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ReviewStatus status;

	protected V1ReviewString() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1ReviewString(ReviewStatus status) {
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public ReviewStatus getStatus() {
		return status;
	}
}
