package com.example.hibernatelab.g14_lead.i2_fixed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Một lần bán thành công: ghi lại bán bao nhiêu cái. Dùng để kiểm tra bất biến "kho còn lại + tổng đã bán = kho ban đầu".
 *
 * BIẾN THỂ : I2 (fixed), schema g14_i2_fixed
 * BASELINE : I2 buggy
 * KHÁC BIỆT: giống hệt package còn lại
 * HẬU QUẢ  : không có riêng
 */
@Entity(name = "G14I2F_Sale")
@Table(schema = "g14_i2_fixed", name = "sale")
public class I2FixedSale {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i2fixed_sale_seq")
	@SequenceGenerator(name = "g14i2fixed_sale_seq", schema = "g14_i2_fixed", sequenceName = "sale_seq", allocationSize = 50)
	private Long id;

	// Cố ý để id thường (không @ManyToOne): bài này học về transaction, không học về quan hệ.
	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected I2FixedSale() {
		// JPA bắt buộc có constructor không tham số
	}

	public I2FixedSale(Long productId, int quantity) {
		this.productId = productId;
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public int getQuantity() {
		return quantity;
	}
}
