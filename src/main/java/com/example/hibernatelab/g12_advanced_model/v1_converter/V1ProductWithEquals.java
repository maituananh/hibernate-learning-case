package com.example.hibernatelab.g12_advanced_model.v1_converter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm có cột {@code tags} lưu dạng chuỗi CSV nhờ {@code AttributeConverter}.
 *
 * BIẾN THỂ : V1E (kiểu có equals)
 * BASELINE : V1 (Tags không có equals)
 * KHÁC BIỆT: kiểu giá trị có equals/hashCode theo nội dung
 * HẬU QUẢ  : xem G12-S11b: so sánh bản chụp cũ với giá trị hiện tại dùng equals
 */
@Entity(name = "G12V1ProductWithEquals")
@Table(schema = "g12_v1_converter", name = "v1_product_equals")
public class V1ProductWithEquals {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12v1_product_equals_seq")
	@SequenceGenerator(name = "g12v1_product_equals_seq", schema = "g12_v1_converter", sequenceName = "v1_product_equals_seq", allocationSize = 50)
	private Long id;

	@Convert(converter = TagsWithEqualsConverter.class)
	@Column(name = "tags")
	private TagsWithEquals tags;

	protected V1ProductWithEquals() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1ProductWithEquals(TagsWithEquals tags) {
		this.tags = tags;
	}

	public Long getId() {
		return id;
	}

	public TagsWithEquals getTags() {
		return tags;
	}
}
