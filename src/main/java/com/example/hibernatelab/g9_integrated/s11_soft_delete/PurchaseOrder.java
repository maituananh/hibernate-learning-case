package com.example.hibernatelab.g9_integrated.s11_soft_delete;

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
 * Đơn hàng (cha) của G9-S11. Collection items trỏ tới OrderItem đã bật {@code @SoftDelete}.
 *
 * BIẾN THỂ : G9-S11 (g9_s11_soft_delete)
 * BASELINE : G3-O0 (INVERSE side, cascade ALL, orphanRemoval)
 * KHÁC BIỆT: phía con OrderItem có @SoftDelete (xoá = UPDATE cờ deleted)
 * HẬU QUẢ  : collection này tự lọc item đã xoá; orphanRemoval cũng thành soft delete
 */
@Entity(name = "G9S11_PurchaseOrder") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g9_s11_soft_delete", name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g9s11_purchase_order_seq")
	@SequenceGenerator(name = "g9s11_purchase_order_seq", schema = "g9_s11_soft_delete", sequenceName = "purchase_order_seq", allocationSize = 50)
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

	public void removeItem(OrderItem item) {
		items.remove(item);
		item.setOrder(null);
	}
}
