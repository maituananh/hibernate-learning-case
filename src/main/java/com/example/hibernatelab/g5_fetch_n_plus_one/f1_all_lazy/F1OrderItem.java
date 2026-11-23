package com.example.hibernatelab.g5_fetch_n_plus_one.f1_all_lazy;

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
 * Dòng hàng của biến thể F1.
 *
 * BIẾN THỂ : F1 (g5_f1_all_lazy)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: mọi quan hệ LAZY ghi tường minh, collection là List (BASELINE, khuyến nghị)
 * HẬU QUẢ  : không tải gì thừa; truy cập quan hệ nào mới tốn SQL đó; ngoài transaction thì LazyInitializationException (G5-S03..S07, S10, S12, S14..S16)
 */
@Entity(name = "G5F1_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1_all_lazy", name = "order_item")
public class F1OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1_order_item_seq", schema = "g5_f1_all_lazy", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private F1PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private F1Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected F1OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F1PurchaseOrder getOrder() {
		return order;
	}

	public F1Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
