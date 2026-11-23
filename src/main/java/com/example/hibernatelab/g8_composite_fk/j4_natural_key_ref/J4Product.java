package com.example.hibernatelab.g8_composite_fk.j4_natural_key_ref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm có khoá tự nhiên {@code sku} (UNIQUE) bên cạnh PK surrogate {@code id}.
 *
 * BIẾN THỂ : J4 (g8_j4_natural_key_ref)
 * BASELINE : J1 (g8_j1_explicit)
 * KHÁC BIỆT: FK một cột trỏ tới cột UNIQUE không phải PK (product.sku) nhờ referencedColumnName
 * HẬU QUẢ  : FK order_item.product_sku -> product(sku); LAZY không tạo được proxy "rỗng" như khi trỏ PK (G8-S06)
 */
@Entity(name = "G8J4_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g8_j4_natural_key_ref", name = "product")
public class J4Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g8j4_product_seq")
	@SequenceGenerator(name = "g8j4_product_seq", schema = "g8_j4_natural_key_ref", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	// unique = true là ĐIỀU KIỆN để cột này làm đích của FK: Postgres từ chối FK trỏ tới cột không có UNIQUE/PK.
	@Column(name = "sku", nullable = false, unique = true)
	private String sku;

	@Column(name = "name", nullable = false)
	private String name;

	protected J4Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public J4Product(String sku, String name) {
		this.sku = sku;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}
}
