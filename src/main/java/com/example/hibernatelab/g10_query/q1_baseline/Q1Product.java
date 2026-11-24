package com.example.hibernatelab.g10_query.q1_baseline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể Q1.
 *
 * BIẾN THỂ : Q1 (g10_q1_baseline)
 * BASELINE : Q1 (g10_q1_baseline)
 * KHÁC BIỆT: không có (đây là baseline của G10): mọi quan hệ LAZY ghi tường minh, collection là List
 * HẬU QUẢ  : không tải gì thừa; G10 chỉ đổi CÁCH VIẾT QUERY trên cùng một bộ mapping, nên kết quả khác nhau
 *            đến từ câu query chứ không từ mapping (G10-S01..S10)
 */
@Entity(name = "G10Q1_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g10_q1_baseline", name = "product")
public class Q1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g10q1_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g10q1_product_seq", schema = "g10_q1_baseline", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected Q1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public Q1Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
