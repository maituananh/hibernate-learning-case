package com.example.hibernatelab.g14_lead.i2_fixed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Sản phẩm trong đợt flash sale, có tồn kho {@code stock}.
 *
 * BIẾN THỂ : I2 (fixed), schema g14_i2_fixed
 * BASELINE : I2 buggy
 * KHÁC BIỆT: thêm @Version (optimistic lock) và service retry khi bị từ chối
 * HẬU QUẢ  : lần ghi đến sau bị từ chối, retry thì đọc lại tồn kho mới và bị chặn đúng (không bán quá)
 */
@Entity(name = "G14I2F_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g14_i2_fixed", name = "product")
public class I2FixedProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g14i2fixed_product_seq")
	@SequenceGenerator(name = "g14i2fixed_product_seq", schema = "g14_i2_fixed", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "stock", nullable = false)
	private int stock;

	// OPTIMISTIC LOCK: Hibernate tự thêm "where version = ?" vào UPDATE và tăng version. Hai transaction cùng đọc version 0
	// thì chỉ một bên UPDATE được; bên kia 0 dòng khớp → OptimisticLockingFailureException (G11-S06a).
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected I2FixedProduct() {
		// JPA bắt buộc có constructor không tham số
	}

	public I2FixedProduct(String name, int stock) {
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
