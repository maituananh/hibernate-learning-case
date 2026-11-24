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
 * BIẾN THỂ : V1 (kiểu không có equals)
 * BASELINE : V1 (Tags không có equals)
 * KHÁC BIỆT: (baseline)
 * HẬU QUẢ  : sửa Tags tại chỗ (add) mà không gán lại: kết quả dirty checking được quan sát ở G12-S11a
 */
@Entity(name = "G12V1Product")
@Table(schema = "g12_v1_converter", name = "v1_product")
public class V1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g12v1_product_seq")
	@SequenceGenerator(name = "g12v1_product_seq", schema = "g12_v1_converter", sequenceName = "v1_product_seq", allocationSize = 50)
	private Long id;

	@Convert(converter = TagsConverter.class)
	@Column(name = "tags")
	private Tags tags;

	protected V1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public V1Product(Tags tags) {
		this.tags = tags;
	}

	public Long getId() {
		return id;
	}

	public Tags getTags() {
		return tags;
	}
}
