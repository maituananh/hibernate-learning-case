package com.example.hibernatelab.g9_integrated.s02_order_update;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm (dữ liệu master dùng chung) của G9-S02.
 *
 * BIẾN THỂ : G9-S02 (g9_s02_order_update)
 * BASELINE : G4-U0
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : không có
 */
@Entity(name = "G9S02_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s02_order_update", name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s02_product_seq")
	@SequenceGenerator(name = "g9s02_product_seq", schema = "g9_s02_order_update", sequenceName = "product_seq", allocationSize = 50)
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
