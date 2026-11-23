package com.example.hibernatelab.g9_integrated.s06_delete_strategies.b_on_delete;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
 * BIẾN THỂ : G9-S06B (g9_s06b_on_delete)
 * BASELINE : G9-S06c (không chiến lược nào)
 * KHÁC BIỆT: @OnDelete(CASCADE) trên PurchaseOrder.customer và OrderItem.order; KHÔNG cascade JPA
 * HẬU QUẢ  : chỉ 1 DELETE customer, PostgreSQL xoá dây chuyền; Hibernate không biết con đã mất (G9-S06b)
 */
@Entity(name = "G9S06B_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s06b_on_delete", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s06b_order_item_seq")
	@SequenceGenerator(name = "g9s06b_order_item_seq", schema = "g9_s06b_on_delete", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	@Column(name = "sku", nullable = false)
	private String sku;

	// OWNING side — FK order_item.order_id.
	@OnDelete(action = OnDeleteAction.CASCADE) // FK sinh ra có "on delete cascade": chính PostgreSQL xoá con khi cha bị xoá
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
