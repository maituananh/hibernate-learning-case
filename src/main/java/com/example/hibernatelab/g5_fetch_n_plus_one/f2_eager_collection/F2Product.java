package com.example.hibernatelab.g5_fetch_n_plus_one.f2_eager_collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể F2.
 *
 * BIẾN THỂ : F2 (g5_f2_eager_collection)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: Customer.orders = EAGER (anti-pattern), phần còn lại như F1
 * HẬU QUẢ  : find(customer) luôn tải orders; findAll() tự sinh N+1 (G5-S13)
 */
@Entity(name = "G5F2_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f2_eager_collection", name = "product")
public class F2Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f2_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f2_product_seq", schema = "g5_f2_eager_collection", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected F2Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public F2Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
