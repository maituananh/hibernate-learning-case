package com.example.hibernatelab.g9_integrated.s04_recursive_tostring;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Đơn hàng với {@code toString()} in cả collection items (đúng thứ Lombok {@code @Data}/{@code @ToString} sinh ra).
 *
 * BIẾN THỂ : G9-S04 (g9_s04_recursive_tostring)
 * BASELINE : quan hệ hai chiều chuẩn của G1-V4
 * KHÁC BIỆT: toString() in quan hệ ở CẢ HAI phía (order → items → mỗi item in lại order → ...)
 * HẬU QUẢ  : gọi toString() trên đối tượng đã nối hai chiều → StackOverflowError (G9-S04)
 */
@Entity(name = "G9S04_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s04_recursive_tostring", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s04_purchase_order_seq")
	@SequenceGenerator(name = "g9s04_purchase_order_seq", schema = "g9_s04_recursive_tostring", sequenceName = "purchase_order_seq", allocationSize = 50)
	private Long id;

	@Column(name = "order_no", nullable = false)
	private String orderNo;

	// INVERSE side — mappedBy = "order".
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	protected PurchaseOrder() {
		// JPA bắt buộc có constructor không tham số
	}

	public PurchaseOrder(String orderNo) {
		this.orderNo = orderNo;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	/** BẪY: in luôn items. Mỗi item lại in order của nó, order lại in items... vô tận. */
	@Override
	public String toString() {
		return "PurchaseOrder{id=" + id + ", orderNo=" + orderNo + ", items=" + items + "}";
	}

	/** CÁCH SỬA: chỉ in field của chính mình (và id của quan hệ nếu cần), tuyệt đối không in quan hệ hai chiều. */
	public String toSafeString() {
		return "PurchaseOrder{id=" + id + ", orderNo=" + orderNo + "}";
	}
}
