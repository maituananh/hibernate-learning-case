package com.example.hibernatelab.g10_query.q2_versioned;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Sản phẩm có giá và @Version của biến thể Q2 (dùng ở G10-S06).
 *
 * BIẾN THỂ : Q2 (g10_q2_versioned)
 * BASELINE : Q1 (g10_q1_baseline)
 * KHÁC BIỆT: có thêm cột {@code @Version} (optimistic lock)
 * HẬU QUẢ  : Hibernate tự tăng version mỗi lần nó UPDATE entity; UPDATE do native SQL tự viết thì KHÔNG đi qua Hibernate
 *            nên version không tăng (G10-S06)
 */
@Entity(name = "G10Q2_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g10_q2_versioned", name = "product")
public class Q2Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g10q2_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g10q2_product_seq", schema = "g10_q2_versioned", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	// Hibernate tự quản lý cột này: +1 mỗi lần chính Hibernate UPDATE dòng.
	@Version
	@Column(name = "version", nullable = false)
	private int version;

	protected Q2Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public Q2Product(String name, BigDecimal price) {
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

	public int getVersion() {
		return version;
	}
}
