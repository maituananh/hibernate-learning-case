package com.example.hibernatelab.g9_integrated.s04_recursive_tostring;

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
 * Dòng hàng với {@code toString()} in ngược lại order.
 *
 * BIẾN THỂ : G9-S04 (g9_s04_recursive_tostring)
 * BASELINE : quan hệ hai chiều chuẩn của G1-V4
 * KHÁC BIỆT: toString() in field {@code order}
 * HẬU QUẢ  : cùng với PurchaseOrder.toString() tạo vòng lặp gọi nhau → StackOverflowError
 */
@Entity(name = "G9S04_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s04_recursive_tostring", name = "order_item")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s04_order_item_seq")
	@SequenceGenerator(name = "g9s04_order_item_seq", schema = "g9_s04_recursive_tostring", sequenceName = "order_item_seq", allocationSize = 50)
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

	public void setOrder(PurchaseOrder order) {
		this.order = order;
	}

	/** BẪY: in luôn order → order.toString() lại in items → item.toString() ... */
	@Override
	public String toString() {
		return "OrderItem{id=" + id + ", sku=" + sku + ", order=" + order + "}";
	}

	/** CÁCH SỬA: không in quan hệ (hoặc chỉ in id của order). */
	public String toSafeString() {
		return "OrderItem{id=" + id + ", sku=" + sku + "}";
	}
}
