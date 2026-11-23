package com.example.hibernatelab.g5_fetch_n_plus_one.f4_subselect;

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
 * Dòng hàng của biến thể F4.
 *
 * BIẾN THỂ : F4 (g5_f4_subselect)
 * BASELINE : F1 (g5_f1_all_lazy)
 * KHÁC BIỆT: như F1 + @Fetch(FetchMode.SUBSELECT) trên Customer.orders
 * HẬU QUẢ  : N+1 gom thành 2 SELECT, câu thứ hai lặp lại query gốc dưới dạng subquery (G5-S09)
 */
@Entity(name = "G5F4_OrderItem") // tên entity phải duy nhất toàn persistence unit
@Table(schema = "g5_f4_subselect", name = "order_item")
public class F4OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "g5f4_order_item_seq")
	// Sequence nằm TRONG schema của biến thể (thiếu schema = ... thì Hibernate tạo nó ở schema "public").
	@SequenceGenerator(name = "g5f4_order_item_seq", schema = "g5_f4_subselect", sequenceName = "order_item_seq", allocationSize = 50)
	private Long id;

	// OWNING side — FK order_item.order_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private F4PurchaseOrder order;

	// OWNING side — FK order_item.product_id nằm ở bảng này.
	// fetch = LAZY ghi tường minh.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private F4Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected F4OrderItem() {
		// JPA bắt buộc có constructor không tham số
	}

	public Long getId() {
		return id;
	}

	public F4PurchaseOrder getOrder() {
		return order;
	}

	public F4Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}
}
