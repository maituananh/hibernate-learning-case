package com.example.hibernatelab.g9_integrated.s05_bulk_modifying;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm có giá, đối tượng của bulk UPDATE ở G9-S05.
 *
 * BIẾN THỂ : G9-S05 (g9_s05_bulk_modifying)
 * BASELINE : không có (tình huống tổng hợp)
 * KHÁC BIỆT: có cột price để bulk "price = price * 1.1"
 * HẬU QUẢ  : bulk JPQL ghi thẳng vào DB, KHÔNG đi qua persistence context nên entity đã tải bị cũ (G9-S05)
 */
@Entity(name = "G9S05_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s05_bulk_modifying", name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s05_product_seq")
	@SequenceGenerator(name = "g9s05_product_seq", schema = "g9_s05_bulk_modifying", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	protected Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public Product(String name, BigDecimal price) {
		this.name = name;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getPrice() {
		return price;
	}
}
