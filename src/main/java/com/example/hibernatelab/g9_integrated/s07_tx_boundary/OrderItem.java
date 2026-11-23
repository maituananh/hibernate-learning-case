package com.example.hibernatelab.g9_integrated.s07_tx_boundary;

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
 * Dòng hàng của G9-S07.
 *
 * BIẾN THỂ : G9-S07 (g9_s07_tx_boundary)
 * BASELINE : G5-F0
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : không có
 */
@Entity(name = "G9S07_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s07_tx_boundary", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s07_order_item_seq")
	@SequenceGenerator(name = "g9s07_order_item_seq", schema = "g9_s07_tx_boundary", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "sku", nullable = false)
	private String sku;

	// OWNING side — FK order_item.order_id.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private PurchaseOrder order;

	protected OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public OrderItem(String sku) {
		this.sku = sku;
	}

	public String getSku() {
		return sku;
	}

	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}
}
