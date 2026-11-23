package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể F1.
 *
 * BIẾN THỂ : F1 (g5_f1_all_lazy)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: mọi quan hệ LAZY ghi tường minh, collection là List (BASELINE, khuyến nghị)
 * HẬU QUẢ  : không tải gì thừa; truy cập quan hệ nào mới tốn SQL đó; ngoài transaction thì LazyInitializationException (G5-S03..S07, S10, S12, S14..S16)
 */
@Entity(name = "G5F1_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1_all_lazy", name = "product")
public class F1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1_product_seq", schema = "g5_f1_all_lazy", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected F1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public F1Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
