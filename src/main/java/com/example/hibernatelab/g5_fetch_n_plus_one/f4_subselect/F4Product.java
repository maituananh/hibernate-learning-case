package com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể F4.
 *
 * BIẾN THỂ : F4 (g5_f4_subselect)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @Fetch(FetchMode.SUBSELECT) trên Customer.orders
 * HẬU QUẢ  : N+1 gom thành 2 SELECT, câu thứ hai lặp lại query gốc dưới dạng subquery (G5-S09)
 */
@Entity(name = "G5F4_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f4_subselect", name = "product")
public class F4Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f4_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f4_product_seq", schema = "g5_f4_subselect", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected F4Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public F4Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
