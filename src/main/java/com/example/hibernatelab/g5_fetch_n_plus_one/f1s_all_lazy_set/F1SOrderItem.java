package com.example.hibernatelab.g5_fetch_n_plus_one.f1s_all_lazy_set;

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
 * Dòng hàng của biến thể F1S.
 *
 * BIẾN THỂ : F1S (g5_f1s_all_lazy_set)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 nhưng collection là Set thay vì List
 * HẬU QUẢ  : Set không phải 'bag' nên fetch 2 collection cùng lúc không bị MultipleBagFetchException, nhưng sinh tích Descartes (G5-S11)
 */
@Entity(name = "G5F1S_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f1s_all_lazy_set", name = "order_item")
public class F1SOrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f1s_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f1s_order_item_seq", schema = "g5_f1s_all_lazy_set", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private F1SPurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private F1SProduct product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected F1SOrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F1SPurchaseOrder getOrder() {
		return order;
	}

	public F1SProduct getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
