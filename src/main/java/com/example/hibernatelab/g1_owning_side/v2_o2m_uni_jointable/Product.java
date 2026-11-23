package com.example.hibernatelab.g1_owning_side.v2_o2m_uni_jointable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm của biến thể V2. Giống hệt ở mọi biến thể G1 (không có quan hệ nào): nó chỉ có mặt
 * để {@link OrderItem} có chỗ trỏ tới, nhờ vậy mỗi biến thể đủ 4 bảng như một hệ thống thật.
 */
@Entity(name = "G1V2_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g1_v2_o2m_uni_jointable", name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g1v2_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g1v2_product_seq", schema = "g1_v2_o2m_uni_jointable", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
