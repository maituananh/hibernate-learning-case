package com.example.hibernatelab.g14_lead.i2_buggy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;


/**
 * Sản phẩm trong đợt flash sale, có tồn kho {@code stock}.
 *
 * BIẾN THỂ : I2 (buggy), schema g14_i2_buggy
 * BASELINE : I2 buggy
 * KHÁC BIỆT: không có cột version, không khoá gì
 * HẬU QUẢ  : hai người mua cùng đọc stock cũ, cùng qua kiểm tra, người ghi sau đè người ghi trước (lost update, G11-S05): bán 12 cái từ kho 10
 */
@Entity(name = "G14I2B_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i2_buggy", name = "product")
public class I2BuggyProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i2buggy_product_seq")
	@SequenceGenerator(name = "g14i2buggy_product_seq", schema = "g14_i2_buggy", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "stock", nullable = false)
	private int stock;

	protected I2BuggyProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public I2BuggyProduct(String name, int stock) {
		this.name = name;
		this.stock = stock;
	}

	public Long getId() {
		return id;
	}

	public int getStock() {
		return stock;
	}

	/** Trừ tồn kho TRONG BỘ NHỚ; DB chỉ đổi khi Hibernate flush (dirty checking). */
	public void decreaseStock(int quantity) {
		this.stock = this.stock - quantity;
	}
}
