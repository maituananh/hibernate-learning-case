package com.example.hibernatelab.g5_fetch_n_plus_one.f0_defaults;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể F0.
 *
 * BIẾN THỂ : F0 (g5_f0_defaults)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: KHÔNG ghi `fetch` ở bất kỳ quan hệ nào: dùng mặc định JPA (@ManyToOne = EAGER, @OneToMany = LAZY)
 * HẬU QUẢ  : @ManyToOne EAGER nên find(order) JOIN luôn customer, còn JPQL thì sinh N+1 (G5-S01, S02)
 */
@Entity(name = "G5F0_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f0_defaults", name = "product")
public class F0Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f0_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f0_product_seq", schema = "g5_f0_defaults", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected F0Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public F0Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
