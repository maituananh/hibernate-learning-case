package com.example.hibernatelab.g9_integrated.s06_delete_strategies.c_no_strategy;

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
 * BIẾN THỂ : G9-S06C (g9_s06c_no_strategy)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: không cascade JPA, không @OnDelete: baseline trần
 * HẬU QUẢ  : xoá customer còn order → vi phạm FK (S06d); muốn xoá phải bulk delete từ dưới lên (S06c)
 */
@Entity(name = "G9S06C_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06c_no_strategy", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06c_order_item_seq")
	@SequenceGenerator(name = "g9s06c_order_item_seq", schema = "g9_s06c_no_strategy", sequenceName = "order_item_seq", allocationSize = 50)
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
