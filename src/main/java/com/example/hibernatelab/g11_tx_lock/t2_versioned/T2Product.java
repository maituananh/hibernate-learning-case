package com.example.hibernatelab.g11_tx_lock.t2_versioned;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Sản phẩm có tồn kho và {@code @Version} (optimistic locking).
 *
 * BIẾN THỂ : T2 (g11_t2_versioned)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: có thêm cột {@code @Version}
 * HẬU QUẢ  : mỗi UPDATE kèm {@code where version = ?}; nếu dòng đã bị transaction khác đổi thì 0 dòng khớp và Hibernate
 *            ném OptimisticLockException thay vì âm thầm ghi đè (G11-S06a)
 */
@Entity(name = "G11T2_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t2_versioned", name = "product")
public class T2Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t2_product_seq")
	@SequenceGenerator(name = "g11t2_product_seq", schema = "g11_t2_versioned", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "stock", nullable = false)
	private int stock;

	// Hibernate tự quản lý cột này: +1 mỗi lần chính Hibernate UPDATE dòng.
	@Version
	@Column(name = "version", nullable = false)
	private int version;

	protected T2Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public T2Product(String name, int stock) {
		this.name = name;
		this.stock = stock;
	}

	public Long getId() {
		return id;
	}

	public int getStock() {
		return stock;
	}

	public int getVersion() {
		return version;
	}

	public void decreaseStock(int quantity) {
		this.stock = this.stock - quantity;
	}
}
