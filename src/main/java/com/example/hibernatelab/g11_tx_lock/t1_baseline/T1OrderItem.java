package com.example.hibernatelab.g11_tx_lock.t1_baseline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dòng hàng của T1.
 *
 * BIẾN THỂ : T1 (g11_t1_baseline)
 * BASELINE : T1 (g11_t1_baseline)
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : không có
 */
@Entity(name = "G11T1_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g11_t1_baseline", name = "order_item")
public class T1OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g11t1_order_item_seq")
	@SequenceGenerator(name = "g11t1_order_item_seq", schema = "g11_t1_baseline", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "sku", nullable = false)
	private String sku;

	// OWNING side — FK order_item.order_id.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private T1PurchaseOrder order;

	protected T1OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public T1OrderItem(String sku) {
		this.sku = sku;
	}

	public String getSku() {
		return sku;
	}
}
