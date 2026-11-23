package com.example.hibernatelab.g9_integrated.s06_delete_strategies.a_cascade_remove;

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
 * Dòng hàng (cháu) ở cuối chuỗi Customer → PurchaseOrder → OrderItem.
 *
 * BIẾN THỂ : G9-S06A (g9_s06a_cascade_remove)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: cascade = REMOVE ở Customer.orders và PurchaseOrder.items (JPA tự xoá theo chuỗi)
 * HẬU QUẢ  : Hibernate TẢI từng collection rồi DELETE từng dòng, con trước cha: nhiều SELECT và nhiều DELETE (G9-S06a)
 */
@Entity(name = "G9S06A_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06a_cascade_remove", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06a_order_item_seq")
	@SequenceGenerator(name = "g9s06a_order_item_seq", schema = "g9_s06a_cascade_remove", sequenceName = "order_item_seq", allocationSize = 50)
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

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public PurchaseOrder getOrder() {
		return order;
	}

	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}
}
