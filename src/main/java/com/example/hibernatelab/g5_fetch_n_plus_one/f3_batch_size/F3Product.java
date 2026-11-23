package com.example.hibernatelab.g5_fetch_n_plus_one.f3_batch_size;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (bảng master, không có quan hệ nào) của biến thể F3.
 *
 * BIẾN THỂ : F3 (g5_f3_batch_size)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @BatchSize(size = 10) trên collection và trên entity
 * HẬU QUẢ  : N+1 gom thành 1 + ceil(5/10) = 2 SELECT (G5-S08)
 */
@Entity(name = "G5F3_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f3_batch_size", name = "product")
@BatchSize(size = 10) // gom việc tải PROXY của entity này theo lô 10
public class F3Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f3_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f3_product_seq", schema = "g5_f3_batch_size", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	protected F3Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public F3Product(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
