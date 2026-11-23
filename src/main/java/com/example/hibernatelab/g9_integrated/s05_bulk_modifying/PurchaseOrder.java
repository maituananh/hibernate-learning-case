package com.example.hibernatelab.g9_integrated.s05_bulk_modifying;

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
 * Đơn hàng (cha) của G9-S05: dùng để chứng minh bulk delete OrderItem bỏ qua cascade/orphanRemoval của cha.
 *
 * BIẾN THỂ : G9-S05 (g9_s05_bulk_modifying)
 * BASELINE : G3-O0 (INVERSE side, cascade ALL, orphanRemoval)
 * KHÁC BIỆT: không có
 * HẬU QUẢ  : collection items trong bộ nhớ có thể còn item mà DB đã xoá sau bulk delete
 */
@Entity(name = "G9S05_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s05_bulk_modifying", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s05_purchase_order_seq")
	@SequenceGenerator(name = "g9s05_purchase_order_seq", schema = "g9_s05_bulk_modifying", sequenceName = "purchase_order_seq", allocationSize = 50)
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

	public Long getId() {
		return id;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}
}
