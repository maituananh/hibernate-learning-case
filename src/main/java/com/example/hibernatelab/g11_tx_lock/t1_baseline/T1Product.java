package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sản phẩm có tồn kho {@code stock}, KHÔNG có {@code @Version}.
 *
 * BIẾN THỂ : T1 (g11_t1_baseline)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: không có (đây là baseline của G11): không có cột version, không khoá gì
 * HẬU QUẢ  : hai transaction cùng đọc stock rồi cùng ghi thì giao dịch ghi sau đè giao dịch ghi trước (lost update, G11-S05)
 */
@Entity(name = "G11T1_Product") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t1_baseline", name = "product")
public class T1Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t1_product_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g11t1_product_seq", schema = "g11_t1_baseline", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "stock", nullable = false)
	private int stock;

	protected T1Product() {
		// JPA bắt buộc có constructor không tham số
	}

	public T1Product(String name, int stock) {
		this.name = name;
		this.stock = stock;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getStock() {
		return stock;
	}

	/** Trừ tồn kho TRONG BỘ NHỚ; DB chỉ đổi khi Hibernate flush (dirty checking). */
	public void decreaseStock(int quantity) {
		this.stock = this.stock - quantity;
	}
}
